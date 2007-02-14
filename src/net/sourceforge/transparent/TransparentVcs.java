package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
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
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.containers.HashSet;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.actions.CheckoutDialog;
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
  @NonNls public static final String TEMPORARY_FILE_SUFFIX = ".deleteAndAdd";
  @NonNls public static final String CLEARTOOL_CMD = "cleartool";
  @NonNls private static final String HIJACKED_EXT = ".hijacked";

  @NonNls private static final String PERSISTENCY_REMOVED_FILE_TAG = "ClearCasePersistencyRemovedFile";
  @NonNls private static final String PERSISTENCY_REMOVED_FOLDER_TAG = "ClearCasePersistencyRemovedFolder";
  @NonNls private static final String PERSISTENCY_RENAMED_FILE_TAG = "ClearCasePersistencyRenamedFile";
  @NonNls private static final String PERSISTENCY_NEW_FILE_TAG = "ClearCasePersistencyNewFile";
  @NonNls private static final String PATH_DELIMITER = "%%%";
  @NonNls private static final String CCASE_KEEP_FILE_SIG = "*.keep";
  @NonNls private static final String CCASE_KEEP_FILE_MID_SIG = "*.keep.*";
  @NonNls private static final String CCASE_CONTRIB_FILE_SIG = "*.contrib";

  @NonNls private static final String WRK_DIR_SIG = "Working directory view: ";
  @NonNls private static final String PRINT_WORKING_VIEW_CMD = "pwv";
  @NonNls private static final String LIST_VIEW_CMD = "lsview";
  @NonNls private static final String LIST_VIEW_KEY1 = "-properties";
  @NonNls private static final String LIST_VIEW_KEY2 = "-full";
  @NonNls private static final String PROPERTIES_SIG = "Properties:";
  @NonNls private static final String SNAPSHOT_SIG = "snapshot";
  @NonNls private static final String DYNAMIC_SIG = "dynamic";

  public HashSet<String> removedFiles;
  public HashSet<String> removedFolders;
  private HashSet<String> newFiles;
  public HashMap<String, String> renamedFiles;

  private ClearCase clearcase;
  private CCaseConfig myTransparentConfig;
  private String   viewName;

  private CCaseCheckinEnvironment checkinEnvironment;
  private CCaseUpdateEnvironment updateEnvironment;
  private ChangeProvider changeProvider;
  private EditFileProvider editProvider;
  private CCaseHistoryProvider historyProvider;

  private VcsShowSettingOption myCheckoutOptions;
  private VcsShowConfirmationOption addConfirmation;
  private VcsShowConfirmationOption removeConfirmation;
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

  public Configurable             getConfigurable() {  return new TransparentConfigurable( myProject );  }
  public CCaseConfig getTransparentConfig()  {  return myTransparentConfig;   }
  public UpdateEnvironment        getUpdateEnvironment()  {  return updateEnvironment;   }
  public ChangeProvider           getChangeProvider()     {  return changeProvider;      }
  public CheckinEnvironment       getCheckinEnvironment() {  return checkinEnvironment;  }
  public EditFileProvider         getEditFileProvider()   {  return editProvider;        }
  public VcsHistoryProvider       getVcsHistoryProvider() {  return historyProvider;     }

  public VcsShowSettingOption      getCheckoutOptions()   {  return myCheckoutOptions;   }
  public VcsShowConfirmationOption getAddConfirmation()   {  return addConfirmation;     }
  public VcsShowConfirmationOption getRemoveConfirmation(){  return removeConfirmation;  }

  public static TransparentVcs getInstance( Project project ) { return project.getComponent(TransparentVcs.class); }

  public void projectOpened()
  {
    initTransparentConfiguration();

    changeProvider = new CCaseChangeProvider( myProject, this );
    updateEnvironment = new CCaseUpdateEnvironment( myProject );
    checkinEnvironment = new CCaseCheckinEnvironment( myProject, this );
    editProvider = new CCaseEditFileProvider( this );
    historyProvider = new CCaseHistoryProvider( myProject );

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance( myProject );
    myCheckoutOptions = vcsManager.getStandardOption( VcsConfiguration.StandardOption.CHECKOUT, this );

    addConfirmation = vcsManager.getStandardConfirmation( VcsConfiguration.StandardConfirmation.ADD, this );
    removeConfirmation = vcsManager.getStandardConfirmation( VcsConfiguration.StandardConfirmation.REMOVE, this );
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
    myTransparentConfig = CCaseConfig.getInstance(myProject);
    myTransparentConfig.addListener(new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            transparentConfigurationChanged();
        }
    });
    transparentConfigurationChanged();
  }

  public void transparentConfigurationChanged()
  {
    if (!getTransparentConfig().offline)
      resetClearCaseFromConfiguration();
  }

  private void resetClearCaseFromConfiguration()
  {
    if (clearcase == null || !getTransparentConfig().implementation.equals(clearcase.getName()))
    {
      try {
        clearcase = new ClearCaseDecorator((ClearCase) Class.forName(getTransparentConfig().implementation).newInstance());
      } catch (Throwable e) {
        Messages.showMessageDialog( WindowManager.getInstance().suggestParentWindow(getProject()),
                                    e.getMessage() + "\nSelecting CommandLineImplementation instead",
                                    "Error while selecting " + getTransparentConfig().implementation +
                                    "implementation", Messages.getErrorIcon());

        getTransparentConfig().implementation = CommandLineClearCase.class.getName();
        clearcase = new ClearCaseDecorator(new CommandLineClearCase());
      }
    }

    if( StringUtil.isNotEmpty( getTransparentConfig().clearcaseRoot) )
    {
      try
      {
        viewName = extractViewName();
        if( viewName != null )
          extractViewType();
      }
      catch( ClearCaseException e )
      {
        //  It is possible that some configuration paths point to an invalid
        //  or obsolete view. 
      }
    }
  }

  private String extractViewName()
  {
    Runner runner = new Runner();
    runner.workingDir = getTransparentConfig().clearcaseRoot;
    runner.run( new String[] { CLEARTOOL_CMD, PRINT_WORKING_VIEW_CMD }, true );
    String output = runner.getOutput();

    List<String> lines = StringUtil.split( output, "\n" );
    for( String line : lines )
    {
      if( line.startsWith( WRK_DIR_SIG ) )
      {
        viewName = line.substring( WRK_DIR_SIG.length() ).trim();
        break;
      }
    }
    return viewName;
  }

  private void extractViewType()
  {
    String output = cleartoolWithOutput( LIST_VIEW_CMD, LIST_VIEW_KEY1, LIST_VIEW_KEY2, viewName );

    List<String> lines = StringUtil.split( output, "\n" );
    for( String line : lines )
    {
      if( line.indexOf( PROPERTIES_SIG ) != -1 )
      {
        if( line.indexOf( SNAPSHOT_SIG ) != -1 )
          getTransparentConfig().setViewSnapshot();
        else
        if( line.indexOf( DYNAMIC_SIG ) != -1 )
          getTransparentConfig().setViewDynamic();

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
      ApplicationManager.getApplication().invokeLater( new Runnable()
        { public void run() { FileTypeManager.getInstance().setIgnoredFilesList( newPat ); } }
      );
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
    ClearCaseFile ccFile = new ClearCaseFile( file, clearcase );
    return ccFile.isElement();
  }

  public boolean isFileIgnored( VirtualFile file )
  {
    ChangeListManager mgr = ChangeListManager.getInstance( myProject );
    return mgr.isIgnoredFile( file );
  }

  public void add2NewFile( VirtualFile file ) {  add2NewFile( file.getPath() );       }
  public void add2NewFile( String path )      {  newFiles.add( path.toLowerCase() );  }
  public boolean containsNew( String path )   {  return newFiles.contains( path.toLowerCase() );   }

  private boolean isCheckInToUseHijack() {
    return myTransparentConfig.offline || myTransparentConfig.checkInUseHijack;
  }


  public void checkinFile( FilePath path, String comment, List<VcsException> errors )
  {
    checkinFile( path.getIOFile(), comment, errors );
  }
  public void checkinFile( File ioFile, String comment, List<VcsException> errors )
  {
    VirtualFile vFile = VcsUtil.getVirtualFile( ioFile );

    try
    {
      FileStatusManager fsmgr = FileStatusManager.getInstance( myProject );
      if( (fsmgr.getStatus( vFile ) == FileStatus.HIJACKED) && isCheckInToUseHijack() )
      {
        checkoutFile( ioFile, true, false, comment );
      }
      getClearCase().checkIn( ioFile, comment );

    }
    catch( Throwable e )
    {
      handleException( e, vFile, errors );
    }
  }

  public boolean checkoutFile( File ioFile, boolean keepHijacked ) throws VcsException
  {
    return checkoutFile( ioFile, keepHijacked, myTransparentConfig.checkoutReserved, "" );
  }
  public boolean checkoutFile( VirtualFile file, boolean keepHijacked ) throws VcsException
  {
    File ioFile = new File( file.getPath() );
    return checkoutFile( ioFile, keepHijacked, myTransparentConfig.checkoutReserved, "" );
  }
  public boolean checkoutFile( File ioFile, boolean keepHijacked, boolean isReserved, String comment )
  {
    VirtualFile file = VcsUtil.getVirtualFile( ioFile );
    if( myCheckoutOptions.getValue() )
    {
      CheckoutDialog dialog = new CheckoutDialog( getProject(), getConfiguration(), file );
      dialog.show();
      if( dialog.getExitCode() == CheckoutDialog.CANCEL_EXIT_CODE )
        return false;

      comment = dialog.getComment();
    }

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

    refreshIdeaFile( file );
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
      if( vFile != null )
        refreshIdeaFile( vFile );
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

      checkoutFile( ioParent, false, false, parentComment );
      getClearCase().add( ioFile, comment);
      getClearCase().checkIn( ioFile, comment);
      checkinFile( ioParent, parentComment, errors );
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

            checkoutFile( ioParent, false, false, parentComment );
            getClearCase().delete( file, StringUtil.isNotEmpty( comment ) ? comment : deleteComment );
            checkinFile( ioParent, parentComment, errors );
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
            renameFile(newFile, oldFile);
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


  public Status  getFileStatus( VirtualFile file ) {  return getFileStatus( new File(file.getPresentableUrl() ));  }
  public Status  getFileStatus( File file )        {  return getClearCase().getStatus( file );  }

  public static String  updateFile( String fileName )  {  String err = cleartoolWithOutput( "update", fileName ); return err; }

  public static void refreshIdeaFile( VirtualFile file )
  {
    file.refresh(true, false);
  }

  public static void cleartool(@NonNls String... subcmd) throws ClearCaseException
  {
//    try { (new Runner()).runAsynchronously(Runner.getCommand( CLEARTOOL_CMD, subcmd )); }
    try { Runner.runAsynchronously(Runner.getCommand( CLEARTOOL_CMD, subcmd )); }
    catch (IOException e) {  throw new ClearCaseException(e.getMessage());  }
  }

  public static String cleartoolWithOutput(@NonNls String... subcmd) throws ClearCaseException
  {
    Runner runner = new Runner();
    runner.run( Runner.getCommand( CLEARTOOL_CMD, subcmd ) );
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
