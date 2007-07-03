package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.containers.HashSet;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.ChangeManagement.CCaseChangeProvider;
import net.sourceforge.transparent.Checkin.CCaseCheckinEnvironment;
import net.sourceforge.transparent.Checkin.CCaseCheckinHandler;
import net.sourceforge.transparent.Checkin.CCaseRollbackEnvironment;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import net.sourceforge.transparent.exceptions.ClearCaseNoServerException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TransparentVcs extends AbstractVcs implements ProjectComponent, JDOMExternalizable
{
  public static final Logger LOG = Logger.getInstance("#net.sourceforge.transparent.TransparentVcs");

  @NonNls public static final String TEMPORARY_FILE_SUFFIX = ".deleteAndAdd";
  @NonNls public static final String CLEARTOOL_CMD = "cleartool";

  @NonNls private static final String PERSISTENCY_REMOVED_FILE_TAG = "ClearCasePersistencyRemovedFile";
  @NonNls private static final String PERSISTENCY_REMOVED_FOLDER_TAG = "ClearCasePersistencyRemovedFolder";
  @NonNls private static final String PERSISTENCY_RENAMED_FILE_TAG = "ClearCasePersistencyRenamedFile";
  @NonNls private static final String PERSISTENCY_RENAMED_FOLDER_TAG = "ClearCasePersistencyRenamedFolder";
  @NonNls private static final String PERSISTENCY_NEW_FILE_TAG = "ClearCasePersistencyNewFile";
  @NonNls private static final String PERSISTENCY_DELETED_FILE_TAG = "ClearCasePersistencyDeletedFile";
  @NonNls private static final String PERSISTENCY_DELETED_FOLDER_TAG = "ClearCasePersistencyDeletedFolder";
  @NonNls private static final String PERSISTENCY_SAVED_ACTIVITY_MAP_TAG = "ClearCasePersistencyActivitiesMap";

  @NonNls private static final String  VIEW_INFO_TAG = "ViewInfo";
  @NonNls private static final String  CONTENT_ROOT_TAG = "ContentRoot";
  @NonNls private static final String  TAG_TAG = "Tag";
  @NonNls private static final String  UUID_TAG = "Uuid";
  @NonNls private static final String  UCM_TAG = "Ucm";
  @NonNls private static final String  DYNAMIC_TAG = "Dynamic";
  @NonNls private static final String  SNAPSHOT_TAG = "Snapshot";
  @NonNls private static final String  ACTIVITY_TAG = "Activity";
  @NonNls private static final String  ACTIVITY_NAME_TAG = "ActivityName";

  @NonNls private static final String PATH_DELIMITER = "%%%";
  @NonNls private static final String CCASE_KEEP_FILE_SIG = "*.keep";
  @NonNls private static final String CCASE_KEEP_FILE_MID_SIG = "*.keep.*";
  @NonNls private static final String CCASE_CONTRIB_FILE_SIG = "*.contrib";
  @NonNls private static final String CCASE_CONTRIB_FILE_MID_SIG = "*.contrib.*";
  @NonNls private static final String CCASE_FINDMERGE_FILE_SIG = "findmerge.log.*";
  @NonNls private static final String HIJACKED_EXT = ".hijacked";

  @NonNls private static final String LIST_VIEW_CMD = "lsview";
  @NonNls private static final String CURRENT_VIEW_SWITCH = "-cview";
  @NonNls private static final String CURRENT_ACTIVITY_SWITCH = "-cact";
  @NonNls private static final String LONG_SWITCH = "-long";
  @NonNls private static final String LIST_ACTIVITY_CMD = "lsactivity";
  @NonNls private static final String CHANGE_ACTIVITY_CMD = "chactivity";

  @NonNls private static final String TAG_SIG = "Tag: ";
  @NonNls private static final String TAG_UUID_SIG = "  View tag uuid:";
  @NonNls private static final String ATTRIBUTES_SIG = "iew attributes:";
  @NonNls private static final String CHECKEDOUT_VERSION_DELIMITER = " from ";
  @NonNls private static final String SNAPSHOT_SIG = "snapshot";
  @NonNls private static final String DYNAMIC_SIG = "dynamic";
  @NonNls private static final String UCM_SIG = "ucmview";

  @NonNls private final static String RESERVED_SIG = "reserved";
  @NonNls private final static String UNRESERVED_SIG = "unreserved";

  @NonNls private static final String ERRORS_TAB_NAME = "ClearCase views";
  @NonNls private static final String INIT_FAILED_TITLE = "Server intialization failed";
  @NonNls private static final String SERVER_UNAVAILABLE_MESSAGE = "\nServer is unavailable, ClearCase support is switched to isOffline mode";
  @NonNls private static final String FAILED_TO_INIT_VIEW_MESSAGE = "Plugin failed to initialize view:\n";

  public static class ViewInfo
  {
    public String tag;
    public String uuid;
    public boolean isSnapshot;
    public boolean isDynamic;
    public boolean isUcm;
    public String activity;
    public String activityName;
  }

/*
  //  Sometimes we need to explicitely distinguish between different
  //  ClearCase implementations since for one of them we use optimized
  //  scheme for file statuses determination. Part of class name is the
  //  best way for that.
//  @NonNls private final static String COMMAND_LINE_CLASS_SIG = "Line";
*/

  //  Resolve the case when parent folder was already checked out by
  //  the presence of this substring in the error message.
  @NonNls private static final String ALREADY_CHECKEDOUT_SIG = "already checked out";
  @NonNls private static final String NOT_A_VOB_OBJECT_SIG = "Not a vob object";

  public static final Key<Boolean> SUCCESSFUL_CHECKOUT = new Key<Boolean>( "SUCCESSFUL_CHECKOUT" );
  public static final Key<Boolean> MERGE_CONFLICT = new Key<Boolean>( "MERGE_CONFLICT" );

  public  HashSet<String> removedFiles;
  public  HashSet<String> removedFolders;
  private HashSet<String> newFiles;
  public  HashMap<String, String> renamedFiles;
  public  HashMap<String, String> renamedFolders;
  public  HashSet<String> deletedFiles;
  public  HashSet<String> deletedFolders;

  //  Keeps for any checked out file the activity which it was checked out with
  private HashMap<String, String> activitiesAssociations;
  private HashMap<String, String> activitiesNames;

  public HashMap<String,ViewInfo> viewsMap;

  private ClearCase clearcase;
  private CCaseConfig config;

  private CCaseCheckinEnvironment checkinEnvironment;
  private CCaseRollbackEnvironment rollbackEnvironment;
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
    deletedFiles = new HashSet<String>();
    deletedFolders = new HashSet<String>();
    renamedFiles = new HashMap<String, String>();
    renamedFolders = new HashMap<String, String>();

    activitiesAssociations = new HashMap<String, String>();
    activitiesNames = new HashMap<String, String>();
    viewsMap = new HashMap<String, ViewInfo>();
  }

  @NotNull
  public String getComponentName()  {  return getName();   }
  public String getName()           {  return getDisplayName();  }
  public String getDisplayName()    {  return "ClearCase";  }
  public String getMenuItemText()   {  return super.getMenuItemText();  }
  public Project getProject()       {  return myProject;   }
//  public boolean isCmdImpl()        {  return getClearCase().getName().indexOf( COMMAND_LINE_CLASS_SIG ) != -1; }
  public static boolean isCmdImpl()        {  return true; }

  public VcsShowSettingOption      getCheckoutOptions()   {  return myCheckoutOptions;   }
  public VcsShowConfirmationOption getAddConfirmation()   {  return addConfirmation;     }
  public VcsShowConfirmationOption getRemoveConfirmation(){  return removeConfirmation;  }

  public Configurable         getConfigurable()       {  return new CCaseConfigurable( myProject );  }
  public CCaseConfig          getConfig()             {  return config;           }
  public ChangeProvider       getChangeProvider()     {  return changeProvider;   }
  public EditFileProvider     getEditFileProvider()   {  return editProvider;     }
  public VcsHistoryProvider   getVcsHistoryProvider() {  return historyProvider;  }
  public CheckinEnvironment   getCheckinEnvironment() {
    return ((config == null) || !config.isOffline) ? checkinEnvironment : null;
  }

  public RollbackEnvironment getRollbackEnvironment() {
    return ((config == null) || !config.isOffline) ? rollbackEnvironment : null;
  }

  public UpdateEnvironment  getUpdateEnvironment()
  {
    //  For dynamic views "Update project" action makes no sence.
    return (config == null) || config.isViewDynamic() ? null : updateEnvironment;
  }

  public static TransparentVcs getInstance( Project project ) { return project.getComponent(TransparentVcs.class); }

  public void initComponent()     {}
  public void disposeComponent()  {}
  public void start() throws VcsException {}
  public void shutdown() throws VcsException {}

  public void projectOpened()
  {
    changeProvider = new CCaseChangeProvider( myProject, this );
    updateEnvironment = new CCaseUpdateEnvironment();
    checkinEnvironment = new CCaseCheckinEnvironment( myProject, this );
    rollbackEnvironment = new CCaseRollbackEnvironment( myProject, this );
    editProvider = new CCaseEditFileProvider( this );
    historyProvider = new CCaseHistoryProvider( myProject );

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance( myProject );
    myCheckoutOptions = vcsManager.getStandardOption( VcsConfiguration.StandardOption.CHECKOUT, this );
    addConfirmation = vcsManager.getStandardConfirmation( VcsConfiguration.StandardConfirmation.ADD, this );
    removeConfirmation = vcsManager.getStandardConfirmation( VcsConfiguration.StandardConfirmation.REMOVE, this );

    vcsManager.registerCheckinHandlerFactory( new CheckinHandlerFactory() {
      @NotNull public CheckinHandler createHandler(final CheckinProjectPanel panel)
      {  return new CCaseCheckinHandler( TransparentVcs.getInstance( myProject ), panel );  }
    } );
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
    config = CCaseConfig.getInstance( myProject );
    LOG.info( ">>> GetCOnfig().Offline == " + config.isOffline );

    if( !config.isOffline )
    {
      resetClearCaseFromConfiguration();
      extractViewProperties();

      //  If configuration has changed, check that we do not operate with
      //  activities in the inappropriate environment.
      if( config.useUcmModel )
      {
        if( !allViewsAreUCM() )
        {
          VcsException warn = new VcsException( "Not all views are of UCM type. Activities processing is suspended.");
          warn.setIsWarning( true );
          AbstractVcsHelper.getInstance( myProject ).showError( warn, ERRORS_TAB_NAME );

          StartupManager.getInstance( myProject ).registerPostStartupActivity( new Runnable() {
            public void run() { ToolWindowManager.getInstance( myProject ).getToolWindow( ToolWindowId.MESSAGES_WINDOW ).activate( null ); }
          });
          
          config.useUcmModel = false;
        }
      }

      if( config.useUcmModel )
      {
        extractViewActivities();
        checkViewsWithoutActions();
      }
    }

    //  Control the appearance of project items so that we can easily
    //  track down potential changes in the repository.
    listener = new VFSListener( getProject() );
    LocalFileSystem.getInstance().addVirtualFileListener( listener );

    addIgnoredFiles();
  }

  public void deactivate()
  {
    LocalFileSystem.getInstance().removeVirtualFileListener( listener );
    ContentRevisionFactory.detachListeners();
  }

//  public void  setClearCase( ClearCase clearCase ) {  clearcase = clearCase;  }
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

  public boolean allViewsAreUCM()
  {
    boolean ucm = true;
    for( ViewInfo info : viewsMap.values() )
      ucm &= info.isUcm;

    return ucm;
  }

  /**
   * Retrieve basic view's properties - Tag, type, activity, uuid.
   * For each content root (the CCase view), issue the command
   * "cleartool lsview -cview" from the working folder equals to that root.
   */
  private void extractViewProperties()
  {
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( myProject );
    VirtualFile[] roots = mgr.getRootsUnderVcs( this );
    try
    {
      for( VirtualFile root : roots )
      {
        if( !viewsMap.containsKey( root.getPath() ) )
        {
          ViewInfo info = new ViewInfo();
          extractViewType( root.getPath(), info );

          viewsMap.put( root.getPath(), info );
        }
      }

      //  Remove those ViewInfo_s which do not correspond to any content roots
      //  currently configured in the application.
      Set<String> storedRoots = new HashSet<String>( viewsMap.keySet() );
      for( String storedRoot : storedRoots )
      {
        boolean isFound = false;
        for( VirtualFile root : roots )
        {
          if( storedRoot.equals( root.getPath() ) )
          {
            isFound = true; break;
          }
        }
        if( !isFound )
        {
          viewsMap.remove( storedRoot );
        }
      }
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

  private static void extractViewType( String viewPath, ViewInfo info ) throws ClearCaseNoServerException
  {
    String output = cleartoolOnLocalPathWithOutput( viewPath, LIST_VIEW_CMD, CURRENT_VIEW_SWITCH, LONG_SWITCH );
    if( isServerDownMessage( output ) )
      throw new ClearCaseNoServerException( output );

    List<String> lines = StringUtil.split( output, "\n" );
    for( String line : lines )
    {
      if( line.startsWith( TAG_SIG ) )
      {
        info.tag = line.substring( TAG_SIG.length() );
      }
      else
      if( line.startsWith( TAG_UUID_SIG ) )
      {
        info.uuid = line.substring( TAG_UUID_SIG.length() );
      }
      else
      if( line.indexOf( ATTRIBUTES_SIG ) != -1 )
      {
        info.isSnapshot = (line.indexOf( SNAPSHOT_SIG ) != -1);
        info.isDynamic = (line.indexOf( DYNAMIC_SIG ) != -1);
        info.isUcm = ( line.indexOf( UCM_SIG ) != -1 );

        break;
      }
    }
  }

  public void extractViewActivities()
  {
    //  1. Collect all activities related somehow to a view. Store the pait of
    //     its names - internal and display name.
    //  2. Retrieve the current activity of a view.

    activitiesNames.clear();
    for( ViewInfo info : viewsMap.values() )
    {
      String output = cleartoolWithOutput( LIST_ACTIVITY_CMD, "-view", info.tag );
      if( !isServerDownMessage( output ) )
      {
        LOG.info( output );
        
        String[] lines = LineTokenizer.tokenize( output, false );
        for( String line : lines )
        {
          Pair<String,String> acts = parseActivities( line );
          activitiesNames.put( acts.getFirst(), acts.getSecond() );
        }
      }
    }

    for( ViewInfo info : viewsMap.values() )
    {
      String output = cleartoolWithOutput( LIST_ACTIVITY_CMD, CURRENT_ACTIVITY_SWITCH, "-view", info.tag );
      if( !isServerDownMessage( output ) )
      {
        LOG.info( output );
        
        Pair<String,String> acts = parseActivities( output );
        info.activity = acts.getFirst();
        info.activityName = acts.getSecond();
      }
    }

    LOG.info( ">>> Extracted Activities:" );
    try
    {
      for( ViewInfo info : viewsMap.values() )
      {
        LOG.info( ">>>\t" + info.activity + "->" + info.activityName );
      }
    }
    catch( Exception e)
    {
      LOG.info( ">>> INTERNAL FAULT DURING ACTIVITES DUMPING. THUS ACTIVITIES PARSING FAILED." );
    }
  }

  private static Pair<String,String> parseActivities( String str )
  {
    final String TEMPLATE = "   \"";

    String name = null;
    String showName = null;
    int index = str.indexOf( TEMPLATE );
    if( index != -1 )
    {
      showName = str.substring( index + TEMPLATE.length(), str.length() - 1 );
      str = str.substring( 0, index );
      String[] fields = str.split( "  " );
      name = fields[ 1 ];
    }
    return new Pair<String,String>( name, showName );
  }

  private void checkViewsWithoutActions()
  {
    Set<String> passiveViews = new HashSet<String>();
    for( ViewInfo info : viewsMap.values() )
    {
      if( StringUtil.isEmpty( info.activityName ))
      {
        passiveViews.add( info.tag );
      }
    }
    if( passiveViews.size() > 0 )
    {
      List<VcsException> list = new ArrayList<VcsException>();
      for( String view : passiveViews )
      {
        VcsException warn = new VcsException( "View " + view + " has no associated activity. Checkout from this view will be problematic.");
        warn.setIsWarning( true );
        list.add( warn );
      }
      AbstractVcsHelper.getInstance( myProject ).showErrors( list, ERRORS_TAB_NAME );
      StartupManager.getInstance( myProject ).registerPostStartupActivity( new Runnable() {
        public void run() { ToolWindowManager.getInstance( myProject ).getToolWindow( ToolWindowId.MESSAGES_WINDOW ).activate( null ); }
      });
    }
  }

  /**
   * Automatically add several patterns (like "*.keep") into the list of ignored
   * file so that they are not becoming the part of the project.
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

    if( patterns.indexOf(CCASE_CONTRIB_FILE_MID_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_CONTRIB_FILE_MID_SIG;

    if( patterns.indexOf(CCASE_FINDMERGE_FILE_SIG) == -1 )
      newPattern += (( newPattern.charAt( newPattern.length() - 1 ) == ';') ? "" : ";" ) + CCASE_FINDMERGE_FILE_SIG;

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

  public boolean fileIsUnderVcs( FilePath path ) {  return VcsUtil.getVcsFor( myProject, path ) == this;  }
  public boolean fileIsUnderVcs( String path )   {  return VcsUtil.isFileUnderVcs( myProject, path ); }

  public boolean fileExistsInVcs( FilePath path )
  {
    //  Non-obvious optimization:
    //  - if the file already has status "MODIFIED" or "HIJACKED", it means
    //    that it is already under this vcs (since we managed to determine its
    //    correct status);
    //  - Otherwise it would have the status "NEW" or "UNVERSIONED" or (as in the case
    //    read-only files) have no status at all.
    FileStatus status = FileStatusManager.getInstance(myProject).getStatus( path.getVirtualFile() );
    if( status == FileStatus.MODIFIED || status == FileStatus.HIJACKED )
      return true;
    else
    if( status == FileStatus.UNKNOWN || status == FileStatus.ADDED )
      return false;
    else
      return fileExistsInVcs( path.getPath() );
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
  public boolean isFolderRemovedForVcs( String path ) {  return deletedFolders.contains( path );  }

  public void addFile2Changelist( File file, @NotNull String changeListName )
  {
    String normName = VcsUtil.getCanonicalLocalPath( file.getPath() );
    activitiesAssociations.put( normName, changeListName );
  }
  public void removeFileFromActivity( String fileName )
  {
    fileName = VcsUtil.getCanonicalLocalPath( fileName );
    activitiesAssociations.remove( fileName );
  }
  @Nullable
  public String getCheckoutActivityForFile( @NotNull String fileName )
  {
    return activitiesAssociations.get( fileName ); 
  }

  @Nullable
  public String getNormalizedActivityName( String activity )
  {
    return activitiesNames.get( activity );
  }
  
  private boolean isCheckInToUseHijack() {  return config.isOffline || config.checkInUseHijack;  }

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

  /*
  public boolean checkoutFile( VirtualFile file, boolean keepHijacked ) throws VcsException
  {
    return checkoutFile( file, keepHijacked, "" );
  }
  */
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
            if( !oldFile.isDirectory() )
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

  public void moveRenameAndCheckInFile( String filePath, String newParentPath, String newName,
                                       String comment, final List<VcsException> errors )
  {
    final File oldFile = new File( filePath );
    final File newFile = new File( newParentPath, newName );

    try
    {
      @NonNls final String modComment = StringUtil.isEmpty(comment) ? "Moved " + filePath + " to " + newName : comment;

      Runnable action = new Runnable(){
        public void run()
        {
          renameFile( newFile, oldFile );
          checkinFile( oldFile, modComment, errors );

          //  Continue transaction only if there was no error on the previous
          //  step.
          if( errors.size() == 0 )
          {
            VcsException error;
            try
            {
              error = tryToCheckout( newFile.getParentFile(), false, modComment );
              if( error != null )
                throw error;
              error = tryToCheckout( oldFile.getParentFile(), false, modComment );
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

  public void changeActivityForLastVersion( FilePath file, String srcActivity, String dstActivity,
                                            List<VcsException> errors )
  {
    //  First get the proper version of the checked out element.
    VirtualFile root = VcsUtil.getVcsRootFor( myProject, file );
    String output = cleartoolOnLocalPathWithOutput( root.getPath(), "lshistory", "-short", file.getPath() );
    String[] lines = LineTokenizer.tokenize( output, false );
    if( lines.length > 0 )
    {
      String version = lines[ 0 ];
      srcActivity = findNormalizedName( srcActivity );
      String dstActivityNorm = findNormalizedName( dstActivity );

      @NonNls Runner runner = new Runner();
      runner.workingDir = root.getPath();
      runner.run( new String[] { CLEARTOOL_CMD, "lsact", "-short", dstActivityNorm }, true );
      if( !runner.isSuccessfull() )
      {
        runner.run( new String[] { CLEARTOOL_CMD, "mkact", "-nc", "-f", "-headline", "\"" + dstActivity + "\"", dstActivityNorm }, true );
      }

      runner.run( new String[] { CLEARTOOL_CMD, CHANGE_ACTIVITY_CMD, "-nc", "-fcset", srcActivity,
                                 "-tcset", dstActivityNorm, CommandLineClearCase.quote( version ) }, true );
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
    LOG.info( "|" + Runner.getCommandLine( cmd ) );

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
    runner.run( Runner.getCommand( CLEARTOOL_CMD, subcmd ), true );
    return runner.getOutput();
  }

  public static String cleartoolOnLocalPathWithOutput( String path, @NonNls String... subcmd) throws ClearCaseException
  {
    Runner runner = new Runner();
    runner.workingDir = path;
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

  private static boolean isMergeConflictMessage( String msg )
  {
    @NonNls final String msgSig1 = "he most recent version";
    @NonNls final String msgSig2 = "is not the predecessor of this";

    return (msg.indexOf( msgSig1 ) != -1) && (msg.indexOf( msgSig2 ) != -1);
  }

  //
  // JDOMExternalizable methods
  //

  public void readExternal(final Element element) throws InvalidDataException
  {
    readElements( element, removedFiles, PERSISTENCY_REMOVED_FILE_TAG, false );
    readElements( element, removedFolders, PERSISTENCY_REMOVED_FOLDER_TAG, false );
    readElements( element, newFiles, PERSISTENCY_NEW_FILE_TAG, true );
    readElements( element, deletedFiles, PERSISTENCY_DELETED_FILE_TAG, false );
    readElements( element, deletedFolders, PERSISTENCY_DELETED_FOLDER_TAG, false );

    readRenamedElements( element, renamedFiles, PERSISTENCY_RENAMED_FILE_TAG, true );
    readRenamedElements( element, renamedFolders, PERSISTENCY_RENAMED_FOLDER_TAG, true );
    readRenamedElements( element, activitiesAssociations, PERSISTENCY_SAVED_ACTIVITY_MAP_TAG, false );

    readViewInfo( element );

    HashSet<String> tmp = new HashSet<String>( newFiles );
    newFiles.clear();
    for( String value : tmp )  newFiles.add( value.toLowerCase() );
  }

  private static void readElements( final Element element, HashSet<String> list, String tag, boolean isExist )
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

  private static void readRenamedElements( final Element element, HashMap<String, String> list,
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

  private void readViewInfo( final Element element )
  {
    List elements = element.getChildren( VIEW_INFO_TAG );
    for (Object cclObj : elements)
    {
      if (cclObj instanceof Element)
      {
        ViewInfo info = new ViewInfo();
        info.tag = ((Element)cclObj).getChild( TAG_TAG ).getValue();
        info.uuid = ((Element)cclObj).getChild( UUID_TAG ).getValue();
        info.isUcm = Boolean.valueOf(((Element)cclObj).getChild( UCM_TAG ).getValue()).booleanValue();
        info.isDynamic = Boolean.valueOf(((Element)cclObj).getChild(DYNAMIC_TAG).getValue()).booleanValue();
        info.isSnapshot = Boolean.valueOf(((Element)cclObj).getChild(SNAPSHOT_TAG).getValue()).booleanValue();
        info.activity = ((Element)cclObj).getChild( ACTIVITY_TAG ).getValue();
        info.activityName = ((Element)cclObj).getChild( ACTIVITY_NAME_TAG ).getValue();

        String root = ((Element)cclObj).getChild( CONTENT_ROOT_TAG ).getValue();
        viewsMap.put( root, info );
      }
    }
  }

  public void writeExternal( final Element element ) throws WriteExternalException
  {
    writeElement( element, removedFiles, PERSISTENCY_REMOVED_FILE_TAG );
    writeElement( element, removedFolders, PERSISTENCY_REMOVED_FOLDER_TAG );
    writeElement( element, newFiles, PERSISTENCY_NEW_FILE_TAG );
    writeElement( element, deletedFiles, PERSISTENCY_DELETED_FILE_TAG );
    writeElement( element, deletedFolders, PERSISTENCY_DELETED_FOLDER_TAG );

    writePairedElement( element, renamedFiles, PERSISTENCY_RENAMED_FILE_TAG );
    writePairedElement( element, renamedFolders, PERSISTENCY_RENAMED_FOLDER_TAG );
    writePairedElement( element, activitiesAssociations, PERSISTENCY_SAVED_ACTIVITY_MAP_TAG );

    writeViewInfo( element );
  }

  private static void writeElement( final Element element, HashSet<String> files, String tag )
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

  private static void writePairedElement( final Element element, HashMap<String, String> files, String tag )
  {
    for( String file : files.keySet() )
    {
      final Element listElement = new Element( tag );
      final String pathPair = file.concat( PATH_DELIMITER ).concat( files.get( file ) );

      listElement.addContent( pathPair );
      element.addContent( listElement );
    }
  }

  private void writeViewInfo( final Element element )
  {
    for( String root : viewsMap.keySet() )
    {
      final ViewInfo info = viewsMap.get( root );
      final Element listElement = new Element( VIEW_INFO_TAG );

      listElement.addContent( new Element( CONTENT_ROOT_TAG ).addContent( root ) );
      listElement.addContent( new Element( TAG_TAG ).addContent( info.tag ) );
      listElement.addContent( new Element( UUID_TAG ).addContent( info.uuid ) );
      listElement.addContent( new Element( UCM_TAG ).addContent( Boolean.toString( info.isUcm ) ) );
      listElement.addContent( new Element( DYNAMIC_TAG ).addContent( Boolean.toString( info.isDynamic ) ) );
      listElement.addContent( new Element( SNAPSHOT_TAG ).addContent( Boolean.toString( info.isSnapshot ) ) );
      listElement.addContent( new Element( ACTIVITY_TAG ).addContent( info.activity) );
      listElement.addContent( new Element( ACTIVITY_NAME_TAG ).addContent( info.activityName) );

      element.addContent( listElement );
    }
  }

  private String findNormalizedName( String activity )
  {
    for( ViewInfo info : viewsMap.values() )
    {
      if( info.activityName.equals( activity ) )
      {
        return info.activity;
      }
    }
    return activity.replace( ' ', '_' );
  }

  @Nullable
  public String getActivityOfViewOfFile( FilePath path )
  {
    String activity = null;
    VirtualFile root = VcsUtil.getVcsRootFor( myProject, path );
    ViewInfo info = viewsMap.get( root.getPath() );
    if( info != null )
    {
      activity = info.activityName;
    }
    return activity;
  }
}