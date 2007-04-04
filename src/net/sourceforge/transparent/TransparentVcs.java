package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.util.containers.HashSet;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import net.sourceforge.transparent.exceptions.ClearCaseNoServerException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class TransparentVcs extends AbstractVcs implements ProjectComponent, JDOMExternalizable
{
  public static final Logger LOG = Logger.getInstance("#net.sourceforge.transparent.TransparentVcs");

  @NonNls public static final String TEMPORARY_FILE_SUFFIX = ".deleteAndAdd";
  @NonNls public static final String CLEARTOOL_CMD = "cleartool";

  @NonNls private static final String PERSISTENCY_REMOVED_FILE_TAG = "ClearCasePersistencyRemovedFile";
  @NonNls private static final String PERSISTENCY_REMOVED_FOLDER_TAG = "ClearCasePersistencyRemovedFolder";
  @NonNls private static final String PERSISTENCY_RENAMED_FILE_TAG = "ClearCasePersistencyRenamedFile";
  @NonNls private static final String PERSISTENCY_NEW_FILE_TAG = "ClearCasePersistencyNewFile";
  @NonNls private static final String PATH_DELIMITER = "%%%";
  @NonNls private static final String CCASE_KEEP_FILE_SIG = "*.keep";
  @NonNls private static final String CCASE_KEEP_FILE_MID_SIG = "*.keep.*";
  @NonNls private static final String CCASE_CONTRIB_FILE_SIG = "*.contrib";
  @NonNls private static final String HIJACKED_EXT = ".hijacked";

  @NonNls private static final String LIST_VIEW_CMD = "lsview";
  @NonNls private static final String CURRENT_VIEW_SWITCH = "-cview";
  @NonNls private static final String PROP_SWITCH = "-properties";
  @NonNls private static final String FULL_SWITCH = "-full";
  @NonNls private static final String PROPERTIES_SIG = "Properties:";
  @NonNls private static final String SNAPSHOT_SIG = "snapshot";
  @NonNls private static final String DYNAMIC_SIG = "dynamic";

  @NonNls private final static String RESERVED_SIG = "reserved";
  @NonNls private final static String UNRESERVED_SIG = "unreserved";

  @NonNls private static final String INIT_FAILED_TITLE = "Server intialization failed";
  @NonNls private static final String SERVER_UNAVAILABLE_MESSAGE = "\nServer is unavailable, ClearCase support is switched to isOffline mode";
  @NonNls private static final String FAILED_TO_INIT_VIEW_MESSAGE = "Plugin failed to initialize view:\n";

  //  Resolve the case when parent folder was already checked out by
  //  the presence of this substring in the error message.
  @NonNls private static final String ALREADY_CHECKEDOUT_SIG = "already checked out";

  public static final Key<Boolean> SUCCESSFUL_CHECKOUT = new Key<Boolean>( "SUCCESSFUL_CHECKOUT" );

  public HashSet<String> removedFiles;
  public HashSet<String> removedFolders;
  private HashSet<String> newFiles;
  public HashMap<String, String> renamedFiles;

  private ClearCase clearcase;
  private CCaseConfig config;

  private CCaseCheckinEnvironment checkinEnvironment;
  private CCaseUpdateEnvironment updateEnvironment;
  private ChangeProvider changeProvider;
  private EditFileProvider editProvider;
  private CCaseHistoryProvider historyProvider;

  private VcsShowSettingOption myCheckoutOptions;
  private VcsShowConfirmationOption addConfirmation;
  private VirtualFileListener listener;

  public TransparentVcs( Project project )
  {
    super( project );

    removedFiles = new HashSet<String>();
    removedFolders = new HashSet<String>();
    newFiles = new HashSet<String>();
    renamedFiles = new HashMap<String, String>();
  }

  @NotNull
  public String getComponentName()  {  return getName();   }
  public String getName()           {  return getDisplayName();  }
  public String getDisplayName()    {  return "ClearCase";  }
  public String getMenuItemText()   {  return super.getMenuItemText();  }
  public Project getProject()       {  return myProject;   }

  public Configurable         getConfigurable()       {  return new TransparentConfigurable( myProject );  }
  public CCaseConfig          getConfig()             {  return config;           }
  public ChangeProvider       getChangeProvider()     {  return changeProvider;   }
  public EditFileProvider     getEditFileProvider()   {  return editProvider;     }
  public VcsHistoryProvider   getVcsHistoryProvider() {  return historyProvider;  }
  public CheckinEnvironment   getCheckinEnvironment()
  {
    return ((config == null) || !config.isOffline) ? checkinEnvironment : null;
  }
  public UpdateEnvironment    getUpdateEnvironment()
  {
    //  For dynamic views "Update project" action makes no sence.
    return (config == null) || config.isViewDynamic() ? null : updateEnvironment;
  }

  public VcsShowSettingOption      getCheckoutOptions()   {  return myCheckoutOptions;   }
  public VcsShowConfirmationOption getAddConfirmation()   {  return addConfirmation;     }

  public static TransparentVcs getInstance( Project project ) { return project.getComponent(TransparentVcs.class); }

  public void projectOpened()
  {
    changeProvider = new CCaseChangeProvider( myProject, this );
    updateEnvironment = new CCaseUpdateEnvironment();
    checkinEnvironment = new CCaseCheckinEnvironment( myProject, this );
    editProvider = new CCaseEditFileProvider( this );
    historyProvider = new CCaseHistoryProvider( myProject );

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance( myProject );
    myCheckoutOptions = vcsManager.getStandardOption( VcsConfiguration.StandardOption.CHECKOUT, this );

    addConfirmation = vcsManager.getStandardConfirmation( VcsConfiguration.StandardConfirmation.ADD, this );
  }

  public void projectClosed() {}

  /**
   *  Attach different component only in the case when current VCS becomes
   *  "active" that is mapped to some module in the project. Otherwise some
   *  listeners and other components may interfere with other useful code or
   *  downgrade the performance.
   */
  public void activate()
  {
    initTransparentConfiguration();

    //  Control the appearance of project items so that we can easily
    //  track down potential changes in the repository.
    listener = new VFSListener( getProject(), this );
    LocalFileSystem.getInstance().addVirtualFileListener( listener );

    addIgnoredFiles();
  }

  public void deactivate()
  {
    LocalFileSystem.getInstance().removeVirtualFileListener( listener );
  }

  public void initComponent()     {}
  public void disposeComponent()  {}
  public void start() throws VcsException {}
  public void shutdown() throws VcsException {}

  public void initTransparentConfiguration()
  {
    config = CCaseConfig.getInstance( myProject );
    config.addListener(new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            transparentConfigurationChanged();
        }
    });
    transparentConfigurationChanged();
  }

  public void transparentConfigurationChanged()
  {
    if (!getConfig().isOffline)
    {
      resetClearCaseFromConfiguration();
      extractViewProperties();
    }
    else
      LOG.info( ">>> GetCOnfig().Offline == true" );
  }

  private void resetClearCaseFromConfiguration()
  {
    if (clearcase == null || !getConfig().implementation.equals(clearcase.getName()))
    {
      try {
        clearcase = new ClearCaseDecorator((ClearCase) Class.forName(getConfig().implementation).newInstance());
      } catch (Throwable e) {
        Messages.showMessageDialog( getProject(), e.getMessage() + "\nSelecting CommandLineImplementation instead",
                                    "Error while selecting " + getConfig().implementation +
                                    "implementation", Messages.getErrorIcon());

        getConfig().implementation = CommandLineClearCase.class.getName();
        clearcase = new ClearCaseDecorator(new CommandLineClearCase());
      }
    }
  }

  /**
   * Take the local associated root for the view, issue the command
   * "cleartool lsview - cview" from the working folder equals to that root.
   */
  private void extractViewProperties()
  {
    if( StringUtil.isNotEmpty( getConfig().clearcaseRoot ) )
    {
      try
      {
         extractViewType();
      }
      catch( ClearCaseNoServerException e )
      {
        Messages.showMessageDialog( getProject(), e.getMessage() + SERVER_UNAVAILABLE_MESSAGE,
                                    INIT_FAILED_TITLE, Messages.getErrorIcon());
        config.isOffline = true;
      }
      catch( ClearCaseException e )
      {
        //  It is possible that some configuration paths point to an invalid
        //  or obsolete view.
        Messages.showMessageDialog( getProject(), FAILED_TO_INIT_VIEW_MESSAGE + e.getMessage(),
                                    INIT_FAILED_TITLE, Messages.getErrorIcon());
      }
    }
    else
      LOG.info( ">>> getConfig().clearcaseRoot is empty" );
  }

  private void extractViewType() throws ClearCaseNoServerException
  {
    LOG.info( "--- Analyzing view type ---" );
    
    String output = cleartoolOnLocalPathWithOutput( LIST_VIEW_CMD, CURRENT_VIEW_SWITCH, PROP_SWITCH, FULL_SWITCH );
    if( isServerDownMessage( output ) )
      throw new ClearCaseNoServerException( output );

    LOG.info( output );
    LOG.info( "--- End view type ---" );

    List<String> lines = StringUtil.split( output, "\n" );
    for( String line : lines )
    {
      if( line.indexOf( PROPERTIES_SIG ) != -1 )
      {
        if( line.indexOf( SNAPSHOT_SIG ) != -1 )
          getConfig().setViewSnapshot();
        else
        if( line.indexOf( DYNAMIC_SIG ) != -1 )
          getConfig().setViewDynamic();

        break;
      }
    }
  }

  /**
   * Automatically add "*.keep" pattern into the list of ignored file so that
   * they are not becoming the part of the project.
   */
  private static void addIgnoredFiles()
  {
    String patterns = FileTypeManager.getInstance().getIgnoredFilesList();
    String newPattern = patterns;

    if( patterns.indexOf(CCASE_KEEP_FILE_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_KEEP_FILE_SIG;

    if( patterns.indexOf(CCASE_KEEP_FILE_MID_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_KEEP_FILE_MID_SIG;

    if( patterns.indexOf(CCASE_CONTRIB_FILE_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_CONTRIB_FILE_SIG;

    if( !newPattern.equals( patterns ))
    {
      final String newPat = newPattern;
      final FileTypeManager mgr = FileTypeManager.getInstance();
      final Runnable action = new Runnable() { public void run() { mgr.setIgnoredFilesList( newPat ); } };
      ApplicationManager.getApplication().invokeLater( new Runnable() {
        public void run() { ApplicationManager.getApplication().runWriteAction( action );  }
      });
    }
  }

  public void  setClearCase( ClearCase clearCase ) {  clearcase = clearCase;  }
  public ClearCase getClearCase() {
    if( clearcase == null )
      resetClearCaseFromConfiguration();

    return clearcase;
  }

  public boolean fileIsUnderVcs (FilePath path)   {  return VcsUtil.isFileUnderVcs( myProject, path ); }
  public boolean fileIsUnderVcs (VirtualFile file){  return VcsUtil.isFileUnderVcs( myProject, file ); }

  public boolean fileExistsInVcs(FilePath path)   {  return fileExistsInVcs( path.getVirtualFile() );  }
  public boolean fileExistsInVcs(VirtualFile file)
  {
    boolean exists = false;
    if( file != null )
    {
      String path = file.getPath();
      if( renamedFiles.containsKey( path ))
        path = renamedFiles.get( path );

      Status status = getStatus( new File( path ) );
      exists = (status != Status.NOT_AN_ELEMENT);
    }
    return exists;
  }

  public boolean isFileIgnored( VirtualFile file )
  {
    ChangeListManager mgr = ChangeListManager.getInstance( myProject );
    return (file != null) && mgr.isIgnoredFile( file );
  }

  public void add2NewFile( VirtualFile file )   {  add2NewFile( file.getPath() );       }
  public void add2NewFile( String path )        {  newFiles.add( path.toLowerCase() );  }
  public void deleteNewFile( VirtualFile file ) {  deleteNewFile( file.getPath() );     }
  public void deleteNewFile( String path )      {  newFiles.remove( path.toLowerCase() );  }
  public boolean containsNew( String path )     {  return newFiles.contains( path.toLowerCase() );   }
  public boolean isFolderRemoved( String path ) {  return removedFolders.contains( path );  }
  
  private boolean isCheckInToUseHijack() {
    return config.isOffline || config.checkInUseHijack;
  }

  public Status getStatus( VirtualFile file ) {  return getClearCase().getStatus( new File( file.getPath() ) );   }
  public Status getStatus( File file )        {  return getClearCase().getStatus( file );   }

  public void checkinFile( FilePath path, String comment, List<VcsException> errors )
  {
    checkinFile( path.getIOFile(), comment, errors );
  }
  public void checkinFile( File ioFile, String comment, List<VcsException> errors )
  {
    VirtualFile vFile = VcsUtil.getVirtualFile( ioFile );
    FileStatusManager fsmgr = FileStatusManager.getInstance( myProject );

    try
    {
      if(( vFile != null ) && (fsmgr.getStatus( vFile ) == FileStatus.HIJACKED) && isCheckInToUseHijack() )
      {
        //  Run checkout in the "non-verbose" mode, that is do not display any
        //  dialogs since we are aready in the Dialoging mode.
        checkoutFile( ioFile, true, false, comment );
      }
      getClearCase().checkIn( ioFile, comment );

    }
    catch( Throwable e )
    {
      handleException( e, vFile, errors );
    }
  }

  public boolean checkoutFile( VirtualFile file, boolean keepHijacked ) throws VcsException
  {
    return checkoutFile( file, keepHijacked, "" );
  }
  public boolean checkoutFile( VirtualFile file, boolean keepHijacked, String comment ) throws VcsException
  {
    File ioFile = new File( file.getPath() );
    return checkoutFile( ioFile, keepHijacked, config.checkoutReserved, comment );
  }
  private boolean checkoutFile( File ioFile, boolean keepHijacked, boolean isReserved, String comment ) throws VcsException
  {
    File newFile = null;
    if( keepHijacked )
    {
      newFile = new File( ioFile.getParentFile().getAbsolutePath(), ioFile.getName() + HIJACKED_EXT );
      ioFile.renameTo( newFile );
    }
    getClearCase().checkOut( ioFile, isReserved, comment );
    if( newFile != null )
    {
      ioFile.delete();
      newFile.renameTo( ioFile );
    }

    return true;
  }

  public void undoCheckoutFile( VirtualFile vFile, List<VcsException> errors )
  {
    File ioFile = new File( vFile.getPath() );
    undoCheckoutFile( vFile, ioFile, errors );
  }

  public void undoCheckoutFile( File file, List<VcsException> errors )
  {
    undoCheckoutFile( null, file, errors );
  }

  private void undoCheckoutFile( VirtualFile vFile, File ioFile, List<VcsException> errors )
  {
    try
    {
      getClearCase().undoCheckOut( ioFile );
    }
    catch( Throwable e )
    {
      handleException( e, vFile, errors );
    }
  }

  public void addFile( VirtualFile file, @NonNls String comment, List<VcsException> errors )
  {
    File ioFile = new File( file.getPath() );
    File ioParent = ioFile.getParentFile();
    if( ioParent != null )
    {
      String parentComment = addToComment( comment, "Adding " + file.getName() );

      if( StringUtil.isEmpty( comment ) )
        comment = "Initial Checkin";

      VcsException error = tryToCheckout( ioParent, false, parentComment );
      if( error != null )
      {
        error.setVirtualFile( file );
        errors.add( error );
        return;
      }

      //  All other exceptions are currently non-workaroundable and
      //  cause the complete operation failure.
      try
      {
        getClearCase().add( ioFile, comment);
        getClearCase().checkIn( ioFile, comment);
        getClearCase().checkIn( ioParent, parentComment );
      }
      catch( ClearCaseException ccExc )
      {
        VcsException e = new VcsException( ccExc.getMessage() );
        e.setVirtualFile( file );
        errors.add( e );
      }
    }
  }

  public void removeFile( final File file, final String comment, final List<VcsException> errors )
  {
    try
    {
      Runnable action = new Runnable()
      {
        public void run()
        {
          //  We can remove only non-checkedout files???
          Status status = getFileStatus( file );
          if( status == Status.CHECKED_OUT )
            undoCheckoutFile( file, errors );

          File ioParent = file.getParentFile();
          if( ioParent.exists() )
          {
            String deleteComment = "Deleting " + file.getName();
            String parentComment = addToComment( comment, deleteComment );


            VcsException error = tryToCheckout( ioParent, false, parentComment );
            if( error != null )
            {
              errors.add( error );
              return;
            }

            //  All other exceptions are currently non-workaroundable and
            //  cause the complete operation failure.
            try
            {
              getClearCase().delete( file, StringUtil.isNotEmpty( comment ) ? comment : deleteComment );
              getClearCase().checkIn( ioParent, parentComment );
            }
            catch( ClearCaseException ccExc )
            {
              VcsException e = new VcsException( ccExc.getMessage() );
              errors.add( e );
            }
          }
        }
      };
      executeAndHandleOtherFileInTheWay( file, action );
    }
    catch (Throwable e)
    {
      handleException( e, (VirtualFile)null, errors );
    }
  }

  public void renameAndCheckInFile( final File oldFile, final String newName,
                                    String comment, final List<VcsException> errors )
  {
    final File newFile = new File( oldFile.getParent(), newName );
    try
    {
      @NonNls final String modComment = StringUtil.isEmpty(comment) ? "Renamed " + oldFile.getName() + " to " + newName : comment;

      Runnable action = new Runnable() {
        public void run() {
          File ioParent = oldFile.getParentFile();
          if( ioParent.exists() )
          {
            renameFile( newFile, oldFile );
            checkinFile( oldFile, modComment, errors );

            getClearCase().checkOut( ioParent, false, modComment );
            getClearCase().move( oldFile, newFile, modComment );
            getClearCase().checkIn( ioParent, modComment );
          }
        }
      };
      executeAndHandleOtherFileInTheWay(oldFile, action );
    }
    catch( Throwable e )
    {
      handleException( e, newFile, errors );
    }
  }

  public void moveRenameAndCheckInFile(String filePath, String newParentPath, String newName,
                                       String comment, final List<VcsException> errors )
  {
    final File oldFile = new File(filePath);
    final File newFile = new File(newParentPath, newName);

    try
    {
      @NonNls final String modComment = StringUtil.isEmpty(comment) ? "Moved " + filePath + " to " + newName : comment;

      Runnable action = new Runnable(){
        public void run() {
          renameFile(newFile, oldFile);
          checkinFile( oldFile, modComment, errors );
          getClearCase().move( oldFile, newFile, modComment );
        }
      };
      executeAndHandleOtherFileInTheWay(oldFile, action );
    }
    catch( Throwable e )
    {
      handleException( e, newFile, errors );
    }
  }

  private static void executeAndHandleOtherFileInTheWay( File targetFile, Runnable command )
  {
    if (isOtherFileInTheWay(targetFile)) {
      executeWithFileInTheWay(targetFile, command);
    } else {
      command.run();
    }
  }

  private static boolean isOtherFileInTheWay(File file) {
    return file.exists();
  }

  private static void executeWithFileInTheWay(File file, Runnable command)
  {
    File tmpFile = new File(file.getPath() + TEMPORARY_FILE_SUFFIX);
    renameFile(file, tmpFile);

    try { command.run();  }
    finally
    {
      if (!tmpFile.renameTo(file)) {
        throw new ClearCaseException("The file '" + file.getAbsolutePath() + "' has been deleted then re-added\n" +
                                     "Check if there is a file '" + tmpFile.getAbsolutePath() + "' and rename it back manually");
      }
    }
  }

  private static void renameFile( File fileToRename, File newFile )
  {
    if (!newFile.getParentFile().exists()) {
      if (!newFile.getParentFile().mkdirs()) {
        throw new ClearCaseException( "Could not create dir " + newFile.getParentFile().getAbsolutePath() + "\nto move " +
                                      fileToRename.getAbsolutePath() + " into");
      }
    }
    if (!fileToRename.renameTo(newFile)) {
      throw new ClearCaseException("Could not move " + fileToRename.getAbsolutePath() + " to " + newFile.getAbsolutePath());
    }
  }

  /**
   * It may appear that parent folder was already checked out - either
   * manually or as the result of the previously failed operation.
   * Ignore the error "... is already checked out..." and store all others. 
   */
  private VcsException tryToCheckout( File file, boolean isReserved, String comment )
  {
    VcsException error = null;
    try
    {
      getClearCase().checkOut( file, isReserved, comment );
    }
    catch( ClearCaseException ccExc )
    {
      String msg = ccExc.getMessage();
      if( msg.indexOf( ALREADY_CHECKEDOUT_SIG ) == - 1 )
      {
        error = new VcsException( msg );
      }
    }
    return error;
  }

  public Status  getFileStatus( VirtualFile file ) {  return getFileStatus( new File(file.getPresentableUrl() ));  }
  public Status  getFileStatus( File file )        {  return getClearCase().getStatus( file );  }

  public static CheckedOutStatus getCheckedOutStatus( File file )
  {
    @NonNls Runner runner = new Runner();
    runner.run( new String[] { CLEARTOOL_CMD, "lscheckout", "-fmt", "%Rf", "-directory", file.getAbsolutePath() }, true );

    if (!runner.isSuccessfull())
      return CheckedOutStatus.NOT_CHECKED_OUT;

    if (runner.getOutput().equalsIgnoreCase( RESERVED_SIG ) )
      return CheckedOutStatus.RESERVED;

    if (runner.getOutput().equalsIgnoreCase( UNRESERVED_SIG ))
      return CheckedOutStatus.UNRESERVED;

    return CheckedOutStatus.NOT_CHECKED_OUT;
  }

  public static String getCheckoutComment(File file)
  {
    @NonNls Runner runner = new Runner();
    runner.run( new String[] { CLEARTOOL_CMD, "lscheckout", "-fmt", "%c", "-directory", file.getAbsolutePath() }, true );

    return !runner.isSuccessfull() ? "" : runner.getOutput();
  }

  public static String  updateFile( String fileName )  {  return cleartoolWithOutput( "update", fileName );  }

  public static void cleartool(@NonNls String... subcmd) throws ClearCaseException
  {
    try { Runner.runAsynchronously( Runner.getCommand( CLEARTOOL_CMD, subcmd ) ); }
    catch (IOException e) {  throw new ClearCaseException(e.getMessage());  }
  }

  public static String cleartoolWithOutput(@NonNls String... subcmd)
  {
    Runner runner = new Runner();
    runner.run( Runner.getCommand( CLEARTOOL_CMD, subcmd ), true );
    return runner.getOutput();
  }

  public String cleartoolOnLocalPathWithOutput(@NonNls String... subcmd) throws ClearCaseException
  {
    Runner runner = new Runner();
    runner.workingDir = getConfig().clearcaseRoot;
    runner.run( Runner.getCommand( CLEARTOOL_CMD, subcmd ), true );
    return runner.getOutput();
  }

  private static void handleException( Throwable e, VirtualFile file, List<VcsException> errors )
  {
    VcsException vcsE = new VcsException( e );
    vcsE.setVirtualFile( file );
    errors.add( vcsE );
  }

  private static void handleException( Throwable e, File file, List<VcsException> errors )
  {
    VirtualFile vFile = VcsUtil.getVirtualFile( file );
    handleException( e, vFile, errors );
  }

  private static String addToComment( String comment, @NonNls String addedText )
  {
    return StringUtil.isNotEmpty( comment ) ? comment + '\n' + addedText : addedText;
  }

  public static boolean isServerDownMessage( String msg )
  {
    @NonNls final String msgSig1 = "albd_contact call failed";
    @NonNls final String msgSig2 = "Unable to connect albd_server";
    @NonNls final String msgSig3 = "can not contact license server";

    return ( msg.indexOf( msgSig1 ) != -1 ) || ( msg.indexOf( msgSig2 ) != -1 ) || ( msg.indexOf( msgSig3 ) != -1 ); 
  }

  //
  // JDOMExternalizable methods
  //

  public void readExternal(final Element element) throws InvalidDataException {
    List files = element.getChildren( PERSISTENCY_REMOVED_FILE_TAG );
    for (Object cclObj : files)
    {
      if (cclObj instanceof Element)
      {
        final Element currentCLElement = ((Element)cclObj);
        final String path = currentCLElement.getValue();

        // Safety check - file can be added again between IDE sessions.
        if( ! new File( path ).exists() )
          removedFiles.add( path );
      }
    }

    files = element.getChildren( PERSISTENCY_REMOVED_FOLDER_TAG );
    for (Object cclObj : files)
    {
      if (cclObj instanceof Element)
      {
        final Element currentCLElement = ((Element)cclObj);
        final String path = currentCLElement.getValue();

        // Safety check - file can be added again between IDE sessions.
        if( ! new File( path ).exists() )
          removedFolders.add( path );
      }
    }

    files = element.getChildren( PERSISTENCY_NEW_FILE_TAG );
    for (Object cclObj : files)
    {
      if (cclObj instanceof Element)
      {
        final Element currentCLElement = ((Element)cclObj);
        final String path = currentCLElement.getValue();

        // Safety check - file can be deleted or changed between IDE sessions.
        if( new File( path ).exists() )
          newFiles.add( path.toLowerCase() );
      }
    }

    files = element.getChildren( PERSISTENCY_RENAMED_FILE_TAG );
    for (Object cclObj : files)
    {
      if (cclObj instanceof Element)
      {
        final Element currentCLElement = ((Element)cclObj);
        final String pathPair = currentCLElement.getValue();
        int delimIndex = pathPair.indexOf( PATH_DELIMITER );
        if( delimIndex != -1 )
        {
          final String newName = pathPair.substring( 0, delimIndex );
          final String oldName = pathPair.substring( delimIndex + PATH_DELIMITER.length() );

          // Safety check - file can be deleted or changed between IDE sessions.
          if( new File( newName ).exists() )
            renamedFiles.put( newName, oldName );
        }
      }
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException
  {
    writeExternalElement( element, removedFiles, PERSISTENCY_REMOVED_FILE_TAG );
    writeExternalElement( element, removedFolders, PERSISTENCY_REMOVED_FOLDER_TAG );
    writeExternalElement( element, newFiles, PERSISTENCY_NEW_FILE_TAG );

    for( String file : renamedFiles.keySet() )
    {
      final Element listElement = new Element(PERSISTENCY_RENAMED_FILE_TAG);
      final String pathPair = file.concat( PATH_DELIMITER ).concat( renamedFiles.get( file ) );

      listElement.addContent( pathPair );
      element.addContent( listElement );
    }
  }

  private static void writeExternalElement( final Element element, HashSet<String> files, String tag )
  {
    //  Sort elements of the list so that there is no perturbation in .ipr/.iml
    //  files in the case when no data has changed.
    String[] sorted = files.toArray( new String[ files.size() ] );
    Arrays.sort( sorted );

    for( String file : sorted )
    {
      final Element listElement = new Element( tag );
      listElement.addContent( file );
      element.addContent( listElement );
    }
  }
}
