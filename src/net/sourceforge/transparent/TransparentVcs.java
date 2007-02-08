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
import com.intellij.openapi.util.WriteExternalException;
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
import org.intellij.plugins.util.LogUtil;
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
  public static final Logger LOG = LogUtil.getLogger();
  @NonNls public static final String TEMPORARY_FILE_SUFFIX = ".deleteAndAdd";
  @NonNls public static final String CLEARTOOL_CMD = "cleartool";

  @NonNls private static final String PERSISTENCY_REMOVED_FILE_TAG = "ClearCasePersistencyRemovedFile";
  @NonNls private static final String PERSISTENCY_REMOVED_FOLDER_TAG = "ClearCasePersistencyRemovedFolder";
  @NonNls private static final String PERSISTENCY_RENAMED_FILE_TAG = "ClearCasePersistencyRenamedFile";
  @NonNls private static final String PERSISTENCY_NEW_FILE_TAG = "ClearCasePersistencyNewFile";
  @NonNls private static final String PATH_DELIMITER = "%%%";
  @NonNls private static final String CCASE_VER_FILE_SIG = "*.keep";

  public HashSet<String> removedFiles;
  public HashSet<String> removedFolders;
  private HashSet<String> newFiles;
  public HashMap<String, String> renamedFiles;

  private ClearCase clearcase;
  private TransparentConfiguration transparentConfig;

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
  public TransparentConfiguration getTransparentConfig()  {  return transparentConfig;   }
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
    transparentConfig = TransparentConfiguration.getInstance(myProject);
    transparentConfig.addListener(new PropertyChangeListener() {
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
        debug("Changing Clearcase interface to " + getTransparentConfig().implementation);
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
  }

  /**
   * Automatically add "*.keep" pattern into the list of ignored file so that
   * they are not becoming the part of the project.
   */
  private static void addIgnoredFiles()
  {
    String patterns = FileTypeManager.getInstance().getIgnoredFilesList();
    String newPattern = patterns;

    if( patterns.indexOf(CCASE_VER_FILE_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_VER_FILE_SIG;

    if( !newPattern.equals( patterns ))
    {
      final String newPat = newPattern;
      ApplicationManager.getApplication().runWriteAction( new Runnable()
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

  public static byte[] getFileContent(String path) throws VcsException {
    debug("enter: getFileContent(" + path + ")");
    return new byte[0];
  }

  public void checkinFile( String path, Object parameters ) throws VcsException
  {
    debug("enter: checkinFile(" + path + ")");

    try {
      ClearCaseFile file = getFile(path);
      file.checkIn((String) parameters, isCheckInToUseHijack());
    } catch (Throwable e) {
      handleException(e);
    }
  }

  private boolean isCheckInToUseHijack() {
      return transparentConfig.offline || transparentConfig.checkInUseHijack;
  }

  public boolean checkoutFile( String path, boolean keepHijacked ) throws VcsException
  {
      String comment = "";
      if( myCheckoutOptions.getValue() )
      {
          CheckoutDialog dialog = new CheckoutDialog( getProject(), getConfiguration(),
                                                      VcsUtil.getVirtualFile( path ) );
          dialog.show();
          if (dialog.getExitCode() == CheckoutDialog.CANCEL_EXIT_CODE) {
              return false;
          }
          comment = dialog.getComment();
      }

      try {
          ClearCaseFile file = getFile(path);
          file.checkOut(transparentConfig.checkoutReserved, keepHijacked, comment);
          refreshIDEA(file);
          return file.isCheckedOut();
      } catch (Throwable e) {
          handleException(e);
          return false;
      }
  }

  public void undoCheckoutFile(String path) throws VcsException
  {
    try
    {
      ClearCaseFile file = getFile( path );
      file.undoCheckOut();
      refreshIDEA( file );
    }
    catch (Throwable e)
    {
        handleException(e);
    }
  }

  public static void refreshIDEA( ClearCaseFile file )
  {
    VirtualFile virtualFile = VcsUtil.getVirtualFile( file.getFile() );
    if (virtualFile != null) {
        virtualFile.refresh(true, false);
    }
  }

  public void addFile(String parentPath, String fileName, Object parameters) throws VcsException
  {
    debug("enter: addFile(" + parentPath + "," + fileName + ")");

    try
    {
      ClearCaseFile file = new ClearCaseFile(new File(parentPath, fileName), getClearCase());
      file.add((String) parameters, false);
    }
    catch (Throwable e) {  handleException(e);  }
  }

  public void removeFile(String path, final Object parameters) throws VcsException
  {
    debug("enter: removeFile(" + path + ")");

    try {
      final File file = new File(path);
      executeAndHandleOtherFileInTheWay(file, new Runnable() {
          public void run() {
              final ClearCaseFile ccFile = getFile(file);
              ccFile.delete((String) parameters, false);
          }
      });
    } catch (Throwable e) {
      handleException(e);
    }
  }

  public void renameAndCheckInFile(final String path, final String newName, final Object parameters) throws VcsException
  {
    debug("enter: renameAndCheckInFile(" + path + ",\n" + "                            " + newName + ")");

    try {
      final File oldFile = new File(path);
      final File newFile = new File(oldFile.getParent(), newName);

      executeAndHandleOtherFileInTheWay(oldFile, new Runnable() {
        public void run() {
            renameFile(newFile, oldFile);
            ClearCaseFile oldCCFile = getFile(oldFile);
            if (!oldCCFile.isCheckedIn()) {
               oldCCFile.checkIn((String) parameters, isCheckInToUseHijack());
            }
            oldCCFile.rename(newName, (String) parameters, false);
        }
      });
    } catch (Throwable e) {  handleException(e);  }
  }

  public void moveRenameAndCheckInFile(String filePath, String newParentPath,
                                       String newName, final Object parameters) throws VcsException
  {
    debug("enter: moveRenameAndCheckInFile(" + filePath + ",\n" + "                                " +
          newParentPath + ",\n" + "                                " + newName + ")");

    try
    {
      final File oldFile = new File(filePath);
      final File newFile = new File(newParentPath, newName);

      executeAndHandleOtherFileInTheWay(oldFile, new Runnable() {
        public void run() {
            renameFile(newFile, oldFile);
            ClearCaseFile oldCCFile = getFile(oldFile);
            ClearCaseFile newCCFile = getFile(newFile);
            if (!oldCCFile.isCheckedIn()) {
                oldCCFile.checkIn((String) parameters, isCheckInToUseHijack());
            }
            oldCCFile.move(newCCFile, (String) parameters, false);
        }
      });
    } catch (Throwable e) {
        handleException(e);
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

  public void addDirectory(String parentPath, String name, Object parameters) throws VcsException
  {
    debug("enter: addDirectory(" + parentPath + ",\n" + "                    " + name + ")");

    addFile(parentPath, name, parameters);
  }

  public void removeDirectory(String path, Object parameters) throws VcsException
  {
    removeFile( path, parameters );
  }

  public void renameDirectory(String path, String newName, Object parameters) throws VcsException
  {
    renameAndCheckInFile(path, newName, parameters);
  }

  public void moveAndRenameDirectory(String path, String newParentPath,
                                     String name, Object parameters) throws VcsException
  {
    moveRenameAndCheckInFile(path, newParentPath, name, parameters);
  }

  public void moveDirectory(String path, String newParentPath, Object parameters) throws VcsException
  {
    debug("enter: moveDirectory(" + path + ",\n" + "                     " + newParentPath + ")");

    moveRenameAndCheckInFile(path, newParentPath, getFile(path).getName(), parameters);
  }


  private ClearCaseFile getFile(String path)  {  return new ClearCaseFile(new File(path), getClearCase());  }
  private ClearCaseFile getFile(File file)    {  return new ClearCaseFile(file, getClearCase());  }
  public  Status        getFileStatus(VirtualFile file) {   return getClearCase().getStatus(new File(file.getPresentableUrl()));  }

  public static void    updateFile( VirtualFile file ) {  updateFile( file.getPath() );  }
  public static String  updateFile( String fileName )  {  String err = cleartoolWithOutput( "update", fileName ); return err; }

  public static void cleartool(@NonNls String... subcmd) throws ClearCaseException
  {
    try { (new Runner()).runAsynchronously(Runner.getCommand( CLEARTOOL_CMD, subcmd )); }
    catch (IOException e) {  throw new ClearCaseException(e.getMessage());  }
  }

  public static String cleartoolWithOutput(@NonNls String... subcmd) throws ClearCaseException
  {
    Runner runner = new Runner();
    runner.run( Runner.getCommand( CLEARTOOL_CMD, subcmd ) );
    return runner.getOutput();
  }

  public static void debug(@NonNls String s)
  {
    if( LOG.isDebugEnabled() ) LOG.debug( s );
  }

  private static void debug(Throwable e)
  {
    LOG.debug(e);
    e.printStackTrace();
  }

  private static void handleException(Throwable e) throws VcsException {
//      Messages.showMessageDialog(_project, e.getMessage(), "Clearcase plugin error", Messages.getErrorIcon());
    debug(e);
    throw new VcsException(e);
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
