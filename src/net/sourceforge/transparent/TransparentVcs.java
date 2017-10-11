package net.sourceforge.transparent;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.Annotations.CCaseAnnotationProvider;
import net.sourceforge.transparent.ChangeManagement.CCaseChangeProvider;
import net.sourceforge.transparent.ChangeManagement.TransparentI;
import net.sourceforge.transparent.Checkin.CCaseCheckinEnvironment;
import net.sourceforge.transparent.Checkin.CCaseRollbackEnvironment;
import net.sourceforge.transparent.History.CCaseDiffProvider;
import net.sourceforge.transparent.History.CCaseHistoryProvider;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jdom.Element;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.function.Function.identity;

public class TransparentVcs extends AbstractVcs implements ProjectComponent, JDOMExternalizable, TransparentI {
  public static final Logger LOG = Logger.getInstance("#net.sourceforge.transparent.TransparentVcs");
  private static final String NAME = "ClearCase";
  private static final VcsKey ourKey = createKey(NAME);

  @NonNls public static final String TEMPORARY_FILE_SUFFIX = ".deleteAndAdd";
  @NonNls public static final String CLEARTOOL_CMD = "cleartool";

  @NonNls private static final String PERSISTENCY_REMOVED_FILE_TAG = "ClearCasePersistencyRemovedFile";
  @NonNls private static final String PERSISTENCY_REMOVED_FOLDER_TAG = "ClearCasePersistencyRemovedFolder";
  @NonNls private static final String PERSISTENCY_RENAMED_FILE_TAG = "ClearCasePersistencyRenamedFile";
  @NonNls private static final String PERSISTENCY_RENAMED_FOLDER_TAG = "ClearCasePersistencyRenamedFolder";
  @NonNls private static final String PERSISTENCY_CHECKED_OUT_FOLDER_TAG = "ClearCasePersistencyCheckedOutFolder";
  @NonNls private static final String PERSISTENCY_NEW_FILE_TAG = "ClearCasePersistencyNewFile";
  @NonNls private static final String PERSISTENCY_MODIFIED_FILE_TAG = "ClearCasePersistencyModifiedFile";
  @NonNls private static final String PERSISTENCY_DELETED_FILE_TAG = "ClearCasePersistencyDeletedFile";
  @NonNls private static final String PERSISTENCY_DELETED_FOLDER_TAG = "ClearCasePersistencyDeletedFolder";

  @NonNls private static final String PATH_DELIMITER = "%%%";
  @NonNls private static final String CCASE_KEEP_FILE_SIG = "*.keep";
  @NonNls private static final String CCASE_KEEP_FILE_MID_SIG = "*.keep.*";
  @NonNls private static final String CCASE_CONTRIB_FILE_SIG = "*.contrib";
  @NonNls private static final String CCASE_CONTRIB_FILE_MID_SIG = "*.contrib.*";
  @NonNls private static final String CCASE_FINDMERGE_FILE_SIG = "findmerge.log.*";
  @NonNls private static final String CCASE_UPDATE_LOG_FILE_SIG = "*.updt";
  @NonNls private static final String HIJACKED_EXT = ".hijacked";

  @NonNls private static final String CHANGE_ACTIVITY_CMD = "chactivity";

  @NonNls private final static String RESERVED_SIG = "reserved";
  @NonNls private final static String UNRESERVED_SIG = "unreserved";

  //  Resolve the case when parent folder was already checked out by
  //  the presence of this substring in the error message.
  @NonNls private static final String ALREADY_CHECKEDOUT_SIG = "already checked out";
  @NonNls private static final String NOT_A_VOB_OBJECT_SIG = "Not a vob object";

  private final BaseOrUCM myBaseOrUCM;

  public static final Key<Boolean> SUCCESSFUL_CHECKOUT = new Key<>("SUCCESSFUL_CHECKOUT");
  public static final Key<Boolean> MERGE_CONFLICT = new Key<>("MERGE_CONFLICT");

  public  Set<String> removedFiles;
  public  Set<String> removedFolders;
  private final Set<VirtualFile> newFiles;
  // newName -> oldName
  public  Map<String, String> renamedFiles;
  // newName -> oldName
  public  Map<String, String> renamedFolders;
  public  Set<String> deletedFiles;
  public  Set<String> deletedFolders;
  public  Set<String> checkedOutFolders;

  //  Used to keep a set of modified files when user switches to the
  //  offline mode. Empty and unused in online mode.
  private final Set<VirtualFile> modifiedFiles;

  private ClearCase clearcase;
  private CCaseConfig config;

  private CCaseDiffProvider myDiffProvider;
  private CCaseCheckinEnvironment checkinEnvironment;
  private CCaseRollbackEnvironment rollbackEnvironment;
  private CCaseUpdateEnvironment updateEnvironment;
  private ChangeProvider changeProvider;
  private EditFileProvider editProvider;
  private CCaseHistoryProvider historyProvider;
  private CCaseAnnotationProvider annotationProvider;

  private VcsShowSettingOption myCheckoutOptions;
  private VcsShowConfirmationOption addConfirmation;
  private VcsShowConfirmationOption removeConfirmation;
  private VirtualFileListener listener;

  // a hack, honestly
  private boolean myActivatePolicyCalculateUCM;
  private static final boolean ourActivatePolicyCalculateUCMDefault = false;

  public TransparentVcs(@NotNull Project project) {
    super( project, NAME);

    removedFiles = Collections.synchronizedSet(new HashSet<String>());
    removedFolders = Collections.synchronizedSet(new HashSet<String>());
    newFiles = Collections.synchronizedSet(new HashSet<VirtualFile>());
    deletedFiles = Collections.synchronizedSet(new HashSet<String>());
    deletedFolders = Collections.synchronizedSet(new HashSet<String>());
    renamedFiles = Collections.synchronizedMap(new HashMap<String, String>());
    renamedFolders = Collections.synchronizedMap(new HashMap<String, String>());
    checkedOutFolders = Collections.synchronizedSet(new java.util.HashSet<String>());
    modifiedFiles = Collections.synchronizedSet(new HashSet<VirtualFile>());

    myBaseOrUCM = new BaseOrUCM(this);
    myActivatePolicyCalculateUCM = ourActivatePolicyCalculateUCMDefault;
  }

  @Override
  @NotNull
  public String getComponentName()  {  return getName();   }
  @Override
  public String getDisplayName()    {  return NAME;  }
  @Override
  public String getMenuItemText()   {  return super.getMenuItemText();  }
  public static boolean isCmdImpl() {  return true; }

  public VcsShowSettingOption      getCheckoutOptions()   {  return myCheckoutOptions;   }
  public VcsShowConfirmationOption getAddConfirmation()   {  return addConfirmation;     }
  public VcsShowConfirmationOption getRemoveConfirmation(){  return removeConfirmation;  }

  @Override
  public Configurable         getConfigurable()       {  return new CCaseConfigurable(myProject );  }
  public CCaseConfig          getConfig()             {  return config;           }
  @Override
  public ChangeProvider       getChangeProvider()     {  return changeProvider;   }
  @Override
  public EditFileProvider     getEditFileProvider()   {  return editProvider;     }
  @Override
  public VcsHistoryProvider   getVcsHistoryProvider() {  return historyProvider;  }
  @Override
  public AnnotationProvider   getAnnotationProvider() {  return annotationProvider;  }
  @Override
  public CheckinEnvironment   createCheckinEnvironment() {
    return ((config == null) || !config.isOffline()) ? checkinEnvironment : null;
  }

  @Override
  public RollbackEnvironment createRollbackEnvironment() {
    return ((config == null) || !config.isOffline()) ? rollbackEnvironment : null;
  }

  @Override
  public UpdateEnvironment  createUpdateEnvironment()
  {
    //  For dynamic views "Update project" action makes no sence.
    CCaseViewsManager viewsMgr = CCaseViewsManager.getInstance( myProject );
    return viewsMgr.isAnySnapshotView() ? updateEnvironment : null;
  }

  public static TransparentVcs getInstance( Project project ) {
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, TransparentVcs.class);
  }

  @Override
  public void projectOpened()
  {
    changeProvider = new CCaseChangeProvider( myProject, this );
    updateEnvironment = new CCaseUpdateEnvironment(myProject);
    checkinEnvironment = new CCaseCheckinEnvironment( myProject, this );
    rollbackEnvironment = new CCaseRollbackEnvironment( myProject, this );
    editProvider = new CCaseEditFileProvider( this );
    historyProvider = new CCaseHistoryProvider( myProject );
    annotationProvider = new CCaseAnnotationProvider( myProject, this );
    myDiffProvider = new CCaseDiffProvider(myProject);

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance( myProject );
    myCheckoutOptions = vcsManager.getStandardOption( VcsConfiguration.StandardOption.CHECKOUT, this );
    addConfirmation = vcsManager.getStandardConfirmation( VcsConfiguration.StandardConfirmation.ADD, this );
    removeConfirmation = vcsManager.getStandardConfirmation( VcsConfiguration.StandardConfirmation.REMOVE, this );
  }

  /**
   *  Attach different component only in the case when current VCS becomes
   *  "active" that is mapped to some module in the project. Otherwise some
   *  listeners and other components may interfere with other useful code or
   *  downgrade the performance.
   */
  @Override
  public void activate()
  {
    config = CCaseConfig.getInstance( myProject );
    config.setHost( this );
    LOG.info( ">>> GetCOnfig().Offline == " + config.isOffline() );

    if( !config.isOffline() )
    {
      resetClearCaseFromConfiguration();
      CCaseViewsManager.getInstance( myProject ).reloadViews();
    }

    //  Control the appearance of project items so that we can easily
    //  track down potential changes in the repository.
    listener = new VFSListener( getProject() );
    LocalFileSystem.getInstance().addVirtualFileListener( listener );
    CommandProcessor.getInstance().addCommandListener( (CommandListener)listener );

    addIgnoredFiles();

    if (myActivatePolicyCalculateUCM) {
      final CCaseSharedConfig sharedConfig = CCaseSharedConfig.getInstance(myProject);
      sharedConfig.setUcmMode(Boolean.TRUE.equals(myBaseOrUCM.isUCMByRoots()));
    } else {
      checkRootsForUCMMismatch();
    }
    myActivatePolicyCalculateUCM = ourActivatePolicyCalculateUCMDefault;
    ReadonlyStatusIsVisibleActivationCheck.check(myProject, NAME);
  }

  public void checkRootsForUCMMismatch() {
    myBaseOrUCM.checkRootsForUCMMismatch();
  }

  @Override
  public void deactivate()
  {
    LocalFileSystem.getInstance().removeVirtualFileListener( listener );
    CommandProcessor.getInstance().removeCommandListener( (CommandListener)listener );
    ContentRevisionFactory.detachListeners();
  }

  public void offlineModeChanged()
  {
    if( !config.isOffline() )
    {
      modifiedFiles.clear();
    }
    else
    {
      ChangeListManager mgr = ChangeListManager.getInstance( myProject );
      List<VirtualFile> list = mgr.getAffectedFiles();
      for( VirtualFile file : list )
      {
        if( mgr.getStatus( file ) == FileStatus.MODIFIED )
        {
          modifiedFiles.add( file );
        }
      }
      mgr.scheduleUpdate();
    }
  }

  public ClearCase getClearCase()
  {
    if( clearcase == null )
      resetClearCaseFromConfiguration();
    return clearcase;
  }

  private void resetClearCaseFromConfiguration()
  {
    if( clearcase == null )
    {
      CommandLineClearCase cc = new CommandLineClearCase();
      cc.setHost( this );
      clearcase = new ClearCaseDecorator( cc );
    }
  }

  /**
   * Automatically add several patterns (like "*.keep") into the list of ignored
   * file so that they are not becoming the part of the project.
   */
  private void addIgnoredFiles()
  {
    String patterns = FileTypeManager.getInstance().getIgnoredFilesList();
    String newPattern = patterns;

    if( patterns.indexOf(CCASE_KEEP_FILE_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_KEEP_FILE_SIG;

    if( patterns.indexOf(CCASE_KEEP_FILE_MID_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_KEEP_FILE_MID_SIG;

    if( patterns.indexOf(CCASE_CONTRIB_FILE_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_CONTRIB_FILE_SIG;

    if( patterns.indexOf(CCASE_CONTRIB_FILE_MID_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_CONTRIB_FILE_MID_SIG;

    if( patterns.indexOf(CCASE_FINDMERGE_FILE_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_FINDMERGE_FILE_SIG;

    if( patterns.indexOf(CCASE_UPDATE_LOG_FILE_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_UPDATE_LOG_FILE_SIG;

    if( !newPattern.equals( patterns ))
    {
      final String newPat = newPattern;
      final FileTypeManager mgr = FileTypeManager.getInstance();
      final Runnable action = () -> mgr.setIgnoredFilesList(newPat );
      ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(action ));
    }

    //  Add file templates to ignore into the change list management also.
    ChangeListManager mgr = ChangeListManager.getInstance( getProject() );
    String[] sigs = new String[] { CCASE_KEEP_FILE_SIG, CCASE_KEEP_FILE_MID_SIG, CCASE_CONTRIB_FILE_SIG,
                                   CCASE_CONTRIB_FILE_MID_SIG, CCASE_FINDMERGE_FILE_SIG, CCASE_UPDATE_LOG_FILE_SIG };
    for( String sig : sigs )
    {
      IgnoredFileBean bean = IgnoredBeanFactory.withMask(sig);
      mgr.addFilesToIgnore( bean );
    }
  }

  @Override
  public boolean fileIsUnderVcs(FilePath path ) {  return VcsUtil.getVcsFor(myProject, path ) == this;  }
  public boolean fileIsUnderVcs( String path )   {  return VcsUtil.isFileUnderVcs( myProject, path ); }

  @Override
  public boolean fileExistsInVcs(FilePath path )
  {
    //  In the case we are offline, reply "NO" since we can not say definitely
    //  anything on the file status (and must not issue any CCase cleartool
    //  command).
    if( config.isOffline() )
      return false;

    VirtualFile vfile = path.getVirtualFile();
    if( vfile != null )
    {
      //  Non-obvious optimization:
      //  - if the file already has status "MODIFIED" or "HIJACKED", it means
      //    that it is already under this vcs (since we managed to determine its
      //    correct status);
      //  - Otherwise it would have the status "NEW" or "UNVERSIONED" or (as in the case
      //    read-only files) have no status at all.

      FileStatus status = FileStatusManager.getInstance(myProject).getStatus( vfile );
      if( status == FileStatus.MODIFIED || status == FileStatus.HIJACKED || FileStatus.NOT_CHANGED == status)
        return true;
      else
      if( status == FileStatus.UNKNOWN || status == FileStatus.ADDED )
        return false;
      else
        return fileExistsInVcs( path.getPath() );
    }
    else
    {
      //  Probably the file which was removed from the project (e.g. as the
      //  result of UpdateProject command).
      return fileExistsInVcs( path.getPath() );
    }
  }

  public boolean fileExistsInVcs( @NotNull String path )
  {
    boolean exists = false;
    try
    {
      path = VcsUtil.getCanonicalLocalPath( path );
      if( renamedFiles.containsKey( path ))
        path = renamedFiles.get( path );

      Status status = getStatus( new File( path ) );
      exists = (status != Status.NOT_AN_ELEMENT);
    }
    catch( RuntimeException e )
    {
      //  <fileExistsInVcs> is an interface method and sometimes is called
      //  when e.g. ClearCase plugin is installed but the ClearCase per se
      //  isn't.
      //  Just ignore the exception.
    }
    return exists;
  }

  @Override
  public boolean isFileIgnored(VirtualFile file )
  {
    ChangeListManager mgr = ChangeListManager.getInstance( myProject );
    return (file != null) && mgr.isIgnoredFile( file );
  }

  @Override
  public boolean isRenamedFile(String path) {
    return renamedFiles.containsKey(path);
  }

  @Override
  public boolean isRenamedFolder(String path) {
    return renamedFolders.containsKey(path);
  }

  @Override
  public boolean isCheckedOutFolder(String path) {
    return checkedOutFolders.contains(path);
  }

  public Set<String> getCheckedOutFolders() {
    return checkedOutFolders;
  }

  public void folderCheckedOut(final String path) {
    checkedOutFolders.add(path);
  }

  // also when turns out is checked out no more
  public void undoCheckout(final String path) {
    checkedOutFolders.remove(path);
  }

  public boolean isFolderRemoved( String path ) {  return removedFolders.contains( path );  }
  public boolean isFolderRemovedForVcs( String path ) {  return deletedFolders.contains( path );  }
  public boolean isWasRenamed( String path )    {  return renamedFiles.containsValue( path );  }
  public boolean isNewOverRenamed( String path ){  return containsNew( path ) && isWasRenamed( path );  }
  public void    removeFolderFromDeleted( @NotNull String path ){
    Set<String> folders = isFolderRemoved( path ) ? removedFolders : deletedFolders;
    folders.remove(path);
  }

  public void add2NewFiles(final Collection<VirtualFile> files) throws VcsException {
    final FilterDescendantVirtualFileConvertible<VirtualFile> filterDescendantVirtualFileConvertible =
      new FilterDescendantVirtualFileConvertible<>(identity(), FilePathComparator.getInstance());
    final ArrayList<VirtualFile> highLevel = new ArrayList<>(files);
    filterDescendantVirtualFileConvertible.doFilter(highLevel);
    for (VirtualFile file : highLevel) {
      add2NewFile(file);
    }
    newFiles.addAll(files);
  }

  public void add2NewFile( VirtualFile file ) throws VcsException {
    addWithParents(file);
  }

  private void addWithParents(VirtualFile file) throws VcsException {
    final VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    VirtualFile current = file;
    final VirtualFile vcsRootFor = ProjectLevelVcsManager.getInstance(myProject).getVcsRootFor(file);
    if (vcsRootFor == null) {
      throw new VcsException("Can not find VCS root for " + file.getPath());
    }
    while (current != null) {
      if (newFiles.contains(current)) return;
      final Status status = getStatus(current);
      if (Status.NOT_AN_ELEMENT.equals(status)) {
        newFiles.add(current);
        if (current.isDirectory()) {
          vcsDirtyScopeManager.dirDirtyRecursively(current);
        }
      } else {
        return;
      }
      if (current.equals(vcsRootFor)) return;
      current = current.getParent();
    }
  }

  public void deleteNewFile( VirtualFile file ) {
    newFiles.remove( file );
  }
  public boolean containsNew( VirtualFile file ){  return newFiles.contains( file );   }
  public boolean containsNew( String path )
  {
    VirtualFile file = VcsUtil.getVirtualFile(new File(path) );
    return file != null && newFiles.contains( file );   
  }

  public boolean containsModified( VirtualFile file ) {  return modifiedFiles.contains( file ); }
  public void    add2ModifiedFile( VirtualFile file ) {  modifiedFiles.add(file); }
  public void    clearModifiedList()                  {  modifiedFiles.clear();     }
  public boolean containsModified( String path )
  {
    VirtualFile file = VcsUtil.getVirtualFile( path );
    return file != null && modifiedFiles.contains( file );
  }

  private boolean isCheckInToUseHijack() {  return config.isOffline() || config.checkInUseHijack;  }

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
        checkoutFile( ioFile, true, comment );
      }
      getClearCase().checkIn( ioFile, comment );

    }
    catch( ClearCaseException e )
    {
      //  In the case of the conflict upon checking in - remember the
      //  particular status of this file for our ChangeProvider.
      if( isMergeConflictMessage( e.getMessage() ))
      {
        //  Sometimes we deal with renamed or moved files. For them we have no
        //  VirtualFile object.
        if( vFile != null )
        {
          vFile.putUserData( MERGE_CONFLICT, true );
        }
      }

      handleException( e, vFile, errors );
    }
    catch( Throwable e )
    {
      handleException( e, vFile, errors );
    }
  }

  public boolean checkoutFile( VirtualFile file, boolean keepHijacked, String comment ) throws VcsException
  {
    File ioFile = new File( file.getPath() );
    return checkoutFile( ioFile, keepHijacked, comment );
  }

  public void checkoutFile(File file, boolean keepHijacked, String comment, boolean allowCheckedOut, boolean noData) throws VcsException {
    if (checkedOutFolders.contains(VcsUtil.getCanonicalLocalPath(file.getPath()))) {
      if (! allowCheckedOut) {
        throw new VcsException("File " + file.getPath() + " is already checked out");
      }
      return;
    }
    if(allowCheckedOut) {
      VcsException error = tryToCheckout( file, comment, noData);
      if( error != null )
        throw error;
    } else {
      checkoutFile( file, keepHijacked, comment );
    }
  }

  private boolean checkoutFile( File ioFile, boolean keepHijacked, String comment ) throws VcsException
  {
    File newFile = null;
    if( keepHijacked )
    {
      newFile = new File( ioFile.getParentFile().getAbsolutePath(), ioFile.getName() + HIJACKED_EXT );
      ioFile.renameTo( newFile );
    }
    getClearCase().checkOut( ioFile, config.checkoutReserved, comment, false);
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
      getClearCase().undoCheckOut(ioFile);
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

      VcsException error = tryToCheckout( ioParent, parentComment, false);
      if( error != null )
      {
        error.setVirtualFile( file );
        errors.add( error );
        return;
      }

      addFileToCheckedoutFolder( ioFile, comment, errors );

      //  All other exceptions are currently non-workaroundable and
      //  cause the complete operation failure.
      try
      {
        getClearCase().checkIn( ioParent, parentComment );
      }
      catch( ClearCaseException ccExc )
      {
        handleException( ccExc, file, errors );
      }
    }
  }

  public void addFileToCheckedoutFolder( File ioFile, String comment, List<VcsException> errors )
  {
    try
    {
      getClearCase().add( ioFile, comment);
      getClearCase().checkIn(ioFile, comment);
    }
    catch( ClearCaseException ccExc )
    {
      handleException( ccExc, ioFile, errors );
    }
  }

  public void removeFile( final File file, final String comment, final List<VcsException> errors )
  {
    try
    {
      Runnable action = () -> {
        //  We can remove only non-checkedout files???
        Status status = getFileStatus( file );
        if( status == Status.CHECKED_OUT )
          undoCheckoutFile( file, errors );

        File ioParent = file.getParentFile();
        if( ioParent.exists() )
        {
          @NonNls String deleteComment = "Deleting " + file.getName();
          String parentComment = addToComment( comment, deleteComment );


          VcsException error = tryToCheckout( ioParent, parentComment, false);
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

      Runnable action = () -> {
        File ioParent = oldFile.getParentFile();
        if( ioParent.exists() )
        {
          renameFile( newFile, oldFile );
          if( !oldFile.isDirectory() )
            checkinFile( oldFile, modComment, errors );

          getClearCase().checkOut( ioParent, config.checkoutReserved, modComment, true);
          //getClearCase().checkOut( oldFile, config.checkoutReserved, modComment, false);
          getClearCase().move( oldFile, newFile, modComment );
          getClearCase().checkIn( ioParent, modComment );
        }
      };
      executeAndHandleOtherFileInTheWay(oldFile, action );
    }
    catch( Throwable e )
    {
      handleException( e, newFile, errors );
    }
  }

  public void moveRenameAndCheckInFile( String filePath, String newParentPath, String newName,
                                       String comment, final List<VcsException> errors )
  {
    final File oldFile = new File( filePath );
    final File newFile = new File( newParentPath, newName );

    try
    {
      @NonNls final String modComment = StringUtil.isEmpty(comment) ? "Moved " + filePath + " to " + newName : comment;

      Runnable action = () -> {
        renameFile( newFile, oldFile );
        if( !oldFile.isDirectory() )
        {
          checkinFile( oldFile, modComment, errors );
        }

        //  Continue transaction only if there was no error on the previous
        //  step.
        if( errors.size() == 0 )
        {
          VcsException error;
          try
          {
            error = tryToCheckout( newFile.getParentFile(), modComment, false);
            if( error != null )
              throw error;
            error = tryToCheckout( oldFile.getParentFile(), modComment, false);
            if( error != null )
              throw error;

            getClearCase().move( oldFile, newFile, modComment );

            checkinFile( newFile.getParentFile(), null, errors );
            checkinFile( oldFile.getParentFile(), null, errors );
          }
          catch( VcsException e )
          {
            handleException( e, newFile, errors );
          }
        }
      };

      executeAndHandleOtherFileInTheWay( oldFile, action );
      //  In the case when everything went to the hell, just keep stuff on
      //  its own place.
      if( errors.size() > 0 )
      {
        renameFile( oldFile, newFile );
      }
    }
    catch( Throwable e )
    {
      handleException( e, newFile, errors );
    }
  }

  private static void executeAndHandleOtherFileInTheWay( File targetFile, Runnable command )
  {
    if( targetFile.exists() ){
      executeWithFileInTheWay(targetFile, command);
    } else {
      command.run();
    }
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

  @SuppressWarnings({"ThrowableInstanceNeverThrown"})
  public void changeActivityForLastVersion( FilePath file, String srcActivity, String dstActivity,
                                            List<VcsException> errors )
  {
    CCaseViewsManager viewsMgr = CCaseViewsManager.getInstance( myProject );
    VirtualFile root = VcsUtil.getVcsRootFor( myProject, file );

    //  First get the proper version of the checked out element.
    String output = cleartoolOnLocalPathWithOutput( root.getPath(), "lshistory", "-short", file.getPath() );
    String[] lines = LineTokenizer.tokenize( output, false );
    if( lines.length > 0 )
    {
      String version = lines[ 0 ];
      srcActivity = viewsMgr.getActivityIdName( srcActivity );
      String dstActivityNorm = viewsMgr.getActivityIdName( dstActivity );

      @NonNls Runner runner = new Runner();
      runner.workingDir = root.getPath();
      runner.run( new String[] { CLEARTOOL_CMD, "lsact", "-short", dstActivityNorm }, true );
      if( !runner.isSuccessfull() )
      {
        runner.run( new String[] { CLEARTOOL_CMD, "mkact", "-nc", "-f", "-headline", "\"" + dstActivity + "\"", dstActivityNorm }, true );
        if( !runner.isSuccessfull() )
        {
          @NonNls String msg = "Error occured while creating an activity (possibly illegal activity name). File(s) is checked to the default activity." +
                               "Error description: " + runner.getOutput();
          errors.add( new VcsException( msg ));
          return;
        }
      }

      runner.run( new String[] { CLEARTOOL_CMD, CHANGE_ACTIVITY_CMD, "-nc", "-fcset", srcActivity,
                                 "-tcset", dstActivityNorm, CommandLineClearCase.quote(version) }, true );
      if( !runner.isSuccessfull() )
      {
        errors.add( new VcsException( runner.getOutput() ));
      }
    }
    else
    {
      errors.add( new VcsException( "Did not manage to retrieve the element version for Activity movement." ));
    }
  }

  /**
   * It may appear that parent folder was already checked out - either
   * manually or as the result of the previously failed operation.
   * Ignore the error "... is already checked out..." and store all others. 
   */
  private VcsException tryToCheckout(File file, String comment, boolean noData)
  {
    VcsException error = null;
    try
    {
      getClearCase().checkOut( file, config.checkoutReserved, comment, noData);
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

  public Status  getFileStatus( VirtualFile file ) {  return getFileStatus(new File(file.getPresentableUrl()));  }
  public Status  getFileStatus( File file )        {  return getClearCase().getStatus( file );  }

  public static CheckedOutStatus getCheckedOutStatus( File file )
  {
    @NonNls Runner runner = new Runner();
    runner.run(new String[]{CLEARTOOL_CMD, "lscheckout", "-fmt", "%Rf", "-directory", file.getAbsolutePath()}, true);

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
    runner.run(new String[]{CLEARTOOL_CMD, "lscheckout", "-fmt", "%c", "-directory", file.getAbsolutePath()}, true);

    String output = runner.getOutput();

    //  We return the output from the command only if the command executed
    //  successfully OR ELSE it does not says us that this is not a valid
    //  repository object.
    //  NB: the latter though is fantastic!!! E.g. for a hijacked file the
    //      command says it has been successfully run but returns a rubbish
    //      as the result.
    return !runner.isSuccessfull() || (output.indexOf( NOT_A_VOB_OBJECT_SIG ) != -1)? "" : output;
  }

  public static void cleartool(@NonNls String... subcmd) throws ClearCaseException
  {
    String[] cmd = Runner.getCommand( CLEARTOOL_CMD, subcmd );
    LOG.debug("|" + Runner.getCommandLine(cmd));

    try { Runner.runAsynchronously( cmd ); }
    catch (IOException e) {  throw new ClearCaseException(e.getMessage());  }
  }

  public static void cleartoolOnLocalPath( String path, @NonNls String... subcmd ) throws ClearCaseException
  {
    try
    {
      String[] cmd = Runner.getCommand( CLEARTOOL_CMD, subcmd );
      Runner.runAsynchronouslyOnPath( path, cmd );
    }
    catch (IOException e)
    {
      throw new ClearCaseException(e.getMessage());
    }
  }

  public static String cleartoolWithOutput(@NonNls String... subcmd)
  {
    Runner runner = new Runner();
    runner.run(Runner.getCommand(CLEARTOOL_CMD, subcmd), true);
    return runner.getOutput();
  }

  public static String cleartoolOnLocalPathWithOutput( String path, @NonNls String... subcmd) throws ClearCaseException
  {
    Runner runner = new Runner();
    runner.workingDir = path;
    runner.run(Runner.getCommand(CLEARTOOL_CMD, subcmd), true);
    return runner.getOutput();
  }

  private static void handleException( Throwable e, VirtualFile file, List<VcsException> errors )
  {
    VcsException vcsE = new VcsException( e );
    vcsE.setVirtualFile( file );
    errors.add(vcsE);
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

    return msg != null && (( msg.indexOf( msgSig1 ) != -1 ) || ( msg.indexOf( msgSig2 ) != -1 ) || ( msg.indexOf( msgSig3 ) != -1 ));
  }

  private static boolean isMergeConflictMessage( String msg )
  {
    @NonNls final String msgSig1 = "he most recent version";
    @NonNls final String msgSig2 = "is not the predecessor of this";

    return msg != null && ((msg.indexOf( msgSig1 ) != -1) && (msg.indexOf( msgSig2 ) != -1));
  }

  //
  // JDOMExternalizable methods
  //

  @Override
  public void readExternal(final Element element) throws InvalidDataException
  {
    HashSet<String> tmp = new HashSet<>();
    readElements( element, removedFiles, PERSISTENCY_REMOVED_FILE_TAG, false );
    readElements( element, removedFolders, PERSISTENCY_REMOVED_FOLDER_TAG, false );
    readElements( element, deletedFiles, PERSISTENCY_DELETED_FILE_TAG, false );
    readElements( element, deletedFolders, PERSISTENCY_DELETED_FOLDER_TAG, false );

    readElements( element, tmp, PERSISTENCY_NEW_FILE_TAG, true );
    convertStringSet2VFileSet( tmp, newFiles );
    readElements( element, tmp, PERSISTENCY_MODIFIED_FILE_TAG, true );
    convertStringSet2VFileSet( tmp, modifiedFiles );

    readRenamedElements( element, renamedFiles, PERSISTENCY_RENAMED_FILE_TAG, true );
    readRenamedElements( element, renamedFolders, PERSISTENCY_RENAMED_FOLDER_TAG, true );
    readElements(element, checkedOutFolders, PERSISTENCY_CHECKED_OUT_FOLDER_TAG, true);
  }

  private static void convertStringSet2VFileSet( final Set<String> set, Set<VirtualFile> vSet )
  {
    vSet.clear();
    for( String path : set ){
      VirtualFile file = VcsUtil.getVirtualFile( path );
      if( file != null )  vSet.add( file );
    }
  }

  private static void readElements( final Element element, Set<String> list, String tag, boolean isExist )
  {
    List files = element.getChildren( tag );
    for (Object cclObj : files)
    {
      if (cclObj instanceof Element)
      {
        final Element currentCLElement = ((Element)cclObj);
        final String path = currentCLElement.getValue();

        // Safety check - file can be added again between IDE sessions.
        if( new File( path ).exists() == isExist )
          list.add( path );
      }
    }
  }

  public static void readRenamedElements( final Element element, Map<String, String> list,
                                          String tag, boolean isExist )
  {
    List files = element.getChildren( tag );
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
          if( new File( newName ).exists() == isExist )
            list.put( newName, oldName );
        }
      }
    }
  }

  @Override
  public void writeExternal(final Element element ) throws WriteExternalException
  {
    writeElement( element, removedFiles, PERSISTENCY_REMOVED_FILE_TAG );
    writeElement( element, removedFolders, PERSISTENCY_REMOVED_FOLDER_TAG );
    writeElement( element, deletedFiles, PERSISTENCY_DELETED_FILE_TAG );
    writeElement(element, deletedFolders, PERSISTENCY_DELETED_FOLDER_TAG);

    HashSet<String> tmp = new HashSet<>();
    for( VirtualFile file : newFiles )
    {
      FileStatus status = FileStatusManager.getInstance( myProject ).getStatus( file );
      if( status == FileStatus.ADDED )
        tmp.add( file.getPath() );
    }
    writeElement( element, tmp, PERSISTENCY_NEW_FILE_TAG );

    for( VirtualFile file : modifiedFiles )
    {
      FileStatus status = FileStatusManager.getInstance( myProject ).getStatus( file );
      if( status == FileStatus.MODIFIED )
        tmp.add( file.getPath() );
    }
    writeElement( element, tmp, PERSISTENCY_MODIFIED_FILE_TAG );

    writePairedElement( element, renamedFiles, PERSISTENCY_RENAMED_FILE_TAG );
    writePairedElement( element, renamedFolders, PERSISTENCY_RENAMED_FOLDER_TAG );
    writeElement(element, checkedOutFolders, PERSISTENCY_CHECKED_OUT_FOLDER_TAG);
  }

  private static void writeElement( final Element element, Set<String> files, String tag )
  {
    //  Sort elements of the list so that there is no perturbation in .ipr/.iml
    //  files in the case when no data has changed.
    String[] sorted = ArrayUtil.toStringArray(files);
    Arrays.sort(sorted);

    for( String file : sorted )
    {
      final Element listElement = new Element( tag );
      listElement.addContent( file );
      element.addContent( listElement );
    }
  }

  public static void writePairedElement( final Element element, Map<String, String> files, String tag )
  {
    for( String file : files.keySet() )
    {
      final Element listElement = new Element( tag );
      final String pathPair = file.concat( PATH_DELIMITER ).concat( files.get( file ) );

      listElement.addContent( pathPair );
      element.addContent( listElement );
    }
  }

  public static VcsKey getKey() {
    return ourKey;
  }

  @Override
  @CalledInAwt
  public void enableIntegration() {
    super.enableIntegration();
    myActivatePolicyCalculateUCM = true;
  }

  @Override
  public boolean areDirectoriesVersionedItems() {
    return true;
  }

  public void reportAdded(final VirtualFile dir, final Collection<VirtualFile> addTo) {
    for (VirtualFile newFile : newFiles) {
      if (newFile != null && newFile.isValid() && VfsUtilCore.isAncestor(dir, newFile, false)) {
        addTo.add(newFile);
      }
    }
  }

  @Override
  public DiffProvider getDiffProvider() {
    return myDiffProvider;
  }

  public Status getStatusSafely(final VirtualFile file) {
    try {
      return getStatus(file);
    } catch (ClearCaseException e) {
      return Status.NOT_AN_ELEMENT;
    }
  }

  public Status getStatusSafely(final File file) {
    try {
      return getClearCase().getStatus(file);
    } catch (ClearCaseException e) {
      return Status.NOT_AN_ELEMENT;
    }
  }

  public String discoverNewName(final String oldName) {
    String canonicName = VcsUtil.getCanonicalLocalPath(oldName);
    if (renamedFiles.containsValue(canonicName)) {
      for (Map.Entry<String, String> entry : renamedFiles.entrySet()) {
        if (Comparing.equal(entry.getValue(), canonicName)) {
          return entry.getKey();
        }
      }
    } else {
      for (Map.Entry<String, String> entry : renamedFolders.entrySet()) {
        if (canonicName.startsWith(entry.getValue())) {
          return entry.getKey() + canonicName.substring(entry.getValue().length());
        }
      }
    }
    return oldName;
  }

  public String discoverOldName( String file )
  {
    String canonicName = VcsUtil.getCanonicalLocalPath(file);
    String oldName = renamedFiles.get(canonicName);
    if( oldName == null ) {
      oldName = renamedFolders.get(canonicName);
      if(oldName == null) {
        oldName = findInRenamedParentFolder(file);
        if( oldName == null )
          oldName = file;
        else
        {
          //  Idiosynchrasic check - whether a RENAMED file is found under the
          //  renamed folder?
          String checkRenamed = renamedFiles.get( oldName );
          if( checkRenamed != null )
            oldName = checkRenamed;
        }
      }
    }

    return oldName;
  }

  private String findInRenamedParentFolder( String name )
  {
    String fileInOldFolder = name;
    for( String folder : renamedFolders.keySet() )
    {
      String oldFolderName = renamedFolders.get( folder );
      if( name.startsWith( folder ) )
      {
        fileInOldFolder = oldFolderName + name.substring( folder.length() );
        break;
      }
    }
    return fileInOldFolder;
  }
}
