package net.sourceforge.transparent;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.containers.HashSet;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import net.sourceforge.transparent.exceptions.ClearCaseNoServerException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Oct 18, 2007
 */
public class CCaseViewsManager implements ProjectComponent, JDOMExternalizable
{
  @NonNls private static final String PERSISTENCY_SAVED_ACTIVITY_MAP_TAG = "ClearCasePersistencyActivitiesMap";
  
  @NonNls private static final String  VIEW_INFO_TAG = "ViewInfo";
  @NonNls private static final String  CONTENT_ROOT_TAG = "ContentRoot";
  @NonNls private static final String  TAG_TAG = "Tag";
  @NonNls private static final String  UUID_TAG = "Uuid";
  @NonNls private static final String  UCM_TAG = "Ucm";
  @NonNls private static final String  SNAPSHOT_TAG = "Snapshot";
  @NonNls private static final String  ACTIVITY_TAG = "Activity";

  @NonNls private static final String TAG_SIG = "Tag: ";
  @NonNls private static final String TAG_UUID_SIG = "  View tag uuid:";
  @NonNls private static final String ATTRIBUTES_SIG = "iew attributes:";
  @NonNls private static final String SNAPSHOT_SIG = "snapshot";
  @NonNls private static final String UCM_SIG = "ucmview";

  @NonNls private static final String LIST_VIEW_CMD = "lsview";
  @NonNls private static final String CURRENT_VIEW_SWITCH = "-cview";
  @NonNls private static final String FORMAT_SWITCH = "-fmt";
  @NonNls private static final String LONG_SWITCH = "-long";
  @NonNls private static final String LIST_ACTIVITY_CMD = "lsactivity";
  @NonNls private static final String LIST_ACTIVITY_FORMAT = "%n <-> %[locked]p <-> %[headline]p <-> %[view]p\\n";
  @NonNls private static final String FIELDS_DELIMITER = " <-> ";
  
  @NonNls private static final String ERRORS_TAB_NAME = "ClearCase views operations";
  @NonNls private static final String SERVER_UNAVAILABLE_MESSAGE = "\nServer is unavailable, ClearCase support is switched to isOffline mode";
  @NonNls private static final String FAILED_TO_INIT_VIEW_MESSAGE = "Plugin failed to initialize view:\n";

  private Project project;

  public HashMap<String, ViewInfo> viewsMapByRoot;
  public HashMap<String, ViewInfo> viewsMapByTag;

  //  Keeps for any checked out file the activity which it was checked out with
  private HashMap<String, String> activitiesAssociations;
  private HashMap<String, ActivityInfo> activitiesMap;


  public static class ViewInfo
  {
    public String tag;
    public String uuid;
    public boolean isSnapshot;
    public boolean isUcm;
    public ActivityInfo currentActivity;
  }

  public static class ActivityInfo
  {
    public ActivityInfo( @NotNull String actName, @NotNull String pubName, @NonNls @NotNull String isObs, String inView )
    {
      name = actName;
      publicName = pubName;
      isObsolete = isObs.equals( "obsolete" );
      activeInView = inView;
    }
    public String name;
    public String publicName;
    public boolean isObsolete;
    public String activeInView;
  }

  public void projectOpened(){}
  public void projectClosed(){}
  public void initComponent()     {}
  public void disposeComponent()  {}
  @NotNull
  public String getComponentName()  {  return "CCaseViewsManager";   }

  public static CCaseViewsManager getInstance( Project project ) { return project.getComponent( CCaseViewsManager.class ); }

  public CCaseViewsManager( Project project )
  {
    this.project = project;

    viewsMapByRoot = new HashMap<String, ViewInfo>();
    viewsMapByTag = new HashMap<String, ViewInfo>();

    activitiesAssociations = new HashMap<String, String>();
    activitiesMap = new HashMap<String, CCaseViewsManager.ActivityInfo>();
  }

  public boolean isAnyUcmView()
  {
    boolean status = false;
    for( ViewInfo info : viewsMapByRoot.values() )
      status = status || info.isUcm;

    return status;
  }

  public boolean isAnySnapshotView()
  {
    boolean status = false;
    for( ViewInfo info : viewsMapByRoot.values() )
      status = status || info.isSnapshot;

    return status;
  }

  @Nullable
  public ViewInfo getViewByRoot( VirtualFile root )
  {
    return (root != null) ? viewsMapByRoot.get( root.getPath() ) : null;
  }

  @Nullable
  public ViewInfo getViewByFile( VirtualFile file )
  {
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
    VirtualFile root = mgr.getVcsRootFor( file );
    return getViewByRoot( root );
  }

  /**
   * Retrieve basic view's properties - Tag, type, activity, uuid.
   * For each content root (the CCase view), issue the command
   * "cleartool lsview -cview" from the working folder equals to that root.
   */
  public void reloadViews()
  {
    TransparentVcs host = TransparentVcs.getInstance( project );
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
    CCaseConfig config = CCaseConfig.getInstance( project );

    VirtualFile[] roots = mgr.getRootsUnderVcs( host );
    try
    {
      loadAbsentViews( roots );
      removeObsoleteViews( roots );
      reconstructByNameMap();

      if( config.useUcmModel )
      {
        extractViewActivities();
        checkViewsWithoutActions();
      }
    }
    catch( ClearCaseNoServerException e )
    {
      String message = SERVER_UNAVAILABLE_MESSAGE + e.getMessage();
      AbstractVcsHelper.getInstance( project ).showError( new VcsException( message ), ERRORS_TAB_NAME );
      
      config.isOffline = true;
    }
    catch( ClearCaseException e )
    {
      //  It is possible that some configuration paths point to an invalid
      //  or obsolete view.
      String message = FAILED_TO_INIT_VIEW_MESSAGE + e.getMessage();
      AbstractVcsHelper.getInstance( project ).showError( new VcsException( message ), ERRORS_TAB_NAME );
    }
  }

  private void loadAbsentViews( VirtualFile[] roots )
  {
    for( VirtualFile root : roots )
    {
      if( !viewsMapByRoot.containsKey( root.getPath() ) )
      {
        ViewInfo info = new ViewInfo();

        extractViewType( root.getPath(), info );
        viewsMapByRoot.put( root.getPath(), info );
      }
    }
  }

  /**
   * Remove those ViewInfo_s which do not correspond to any content roots
   * currently configured in the application.
   */
  private void removeObsoleteViews( VirtualFile[] roots )
  {
    Set<String> storedRoots = new HashSet<String>( viewsMapByRoot.keySet() );
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
        viewsMapByRoot.remove( storedRoot );
      }
    }
  }
  
  private static void extractViewType( String viewPath, ViewInfo info ) throws ClearCaseNoServerException
  {
    String output = TransparentVcs.cleartoolOnLocalPathWithOutput( viewPath, LIST_VIEW_CMD, CURRENT_VIEW_SWITCH, LONG_SWITCH );
    if( TransparentVcs.isServerDownMessage( output ) )
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
        //  When analyzing the type of the view (dynamic or snapshot):
        //  value "snapshot" appears in "View attributes:..." line only in the
        //  case of snapshot view (lol). If the value is not present, assume the
        //  view is dynamic.
        info.isSnapshot = (line.indexOf( SNAPSHOT_SIG ) != -1);
        info.isUcm = (line.indexOf( UCM_SIG ) != -1);
        break;
      }
    }
  }

  private void reconstructByNameMap()
  {
    viewsMapByTag.clear();
    for( ViewInfo view : viewsMapByRoot.values() )
    {
      viewsMapByTag.put( view.tag, view );
    }
  }

  private void checkViewsWithoutActions()
  {
    Set<String> passiveViews = new HashSet<String>();
    for( ViewInfo info : viewsMapByRoot.values() )
    {
      if( info.currentActivity == null )
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
      AbstractVcsHelper.getInstance( project ).showErrors( list, ERRORS_TAB_NAME );
      StartupManager.getInstance( project ).registerPostStartupActivity( new Runnable() {
        public void run() { ToolWindowManager.getInstance( project ).getToolWindow( ToolWindowId.MESSAGES_WINDOW ).activate( null ); }
      });
    }
  }

  /**
   * Iterate over views, issue "lsactivity" for the view, collect all activities
   * associated with the view. Remember its status - normal or obsolete ("locked"
   * is not supported now).
   */
  public void extractViewActivities()
  {
    activitiesMap.clear();
    for( CCaseViewsManager.ViewInfo info : viewsMapByRoot.values() )
    {
      if( info.isUcm )
      {
        String output = TransparentVcs.cleartoolWithOutput( LIST_ACTIVITY_CMD, "-view", info.tag, FORMAT_SWITCH, LIST_ACTIVITY_FORMAT );
        if( TransparentVcs.isServerDownMessage( output ) )
          return;

        TransparentVcs.LOG.info( output );

        //  Reset values so that we can always determine that we did not manage
        //  to correctly parse "lsactivity" command's output.
        info.currentActivity = null;

        String[] lines = LineTokenizer.tokenize( output, false );
        for( String line : lines )
        {
          ActivityInfo actInfo = parseActivities( line );
          if( actInfo != null ) //  successful parse?
          {
            activitiesMap.put( actInfo.name, actInfo );
            associateActivityWithView( actInfo );
          }
        }
      }
    }

    TransparentVcs.LOG.info( ">>> Extracted Activities:" );
    for( ViewInfo info : viewsMapByRoot.values() )
    {
      if( info.isUcm )
      {
        TransparentVcs.LOG.info( ">>>\t" + info.tag );
        
        if( info.currentActivity != null )
          TransparentVcs.LOG.info( " -> " + info.currentActivity.publicName );
        else
          TransparentVcs.LOG.info( " has no default activity" );
      }
    }
  }

  @Nullable
  private static ActivityInfo parseActivities( String str )
  {
    ActivityInfo info = null;
    String[] tokens = str.split( FIELDS_DELIMITER );
    if( tokens.length == 4 ) //  successful extraction
      info = new ActivityInfo( tokens[ 0 ], tokens[ 2 ], tokens[ 1 ], tokens[ 3 ] );
    else
    if( tokens.length == 3 ) //  activity is not current for any view.
      info = new ActivityInfo( tokens[ 0 ], tokens[ 2 ], tokens[ 1 ], null );

    return info;
  }

  private void associateActivityWithView( @NotNull ActivityInfo activityInfo )
  {
    if( activityInfo.activeInView != null )
    {
      ViewInfo info = viewsMapByTag.get( activityInfo.activeInView );
      if( info != null )
      {
        info.currentActivity = activityInfo;
      }
    }
  }

  public void addFile2Changelist( String fileName, @NotNull String changeListName )
  {
    String normName = VcsUtil.getCanonicalLocalPath( fileName );
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
  public String getActivityDisplayName( String activity )
  {
    ActivityInfo info = activitiesMap.get( activity );
    return (info != null) ? info.publicName : null;
  }


  public String getActivityIdName( String activity )
  {
    for( ActivityInfo info : activitiesMap.values() )
    {
      if( info.publicName.equals( activity ) )
        return info.name;
    }

    return activity.replace( ' ', '_' );
  }

  @Nullable
  public String getActivityOfViewOfFile( FilePath path )
  {
    String activity = null;
    VirtualFile root = VcsUtil.getVcsRootFor( project, path );
    ViewInfo info = viewsMapByRoot.get( root.getPath() );
    if( info != null && info.isUcm && info.currentActivity != null )
    {
      activity = info.currentActivity.publicName;
    }
    return activity;
  }

  /**
   * Collect all activities which are "current" or "active" in their views. 
   */
  public List<String> getDefaultActivities()
  {
    List<String> activities = new ArrayList<String>();
    for( ActivityInfo info : activitiesMap.values() )
    {
      if( info.activeInView != null )
        activities.add( info.publicName );
    }

    return activities;
  }

  //
  // JDOMExternalizable methods
  //
  public void readExternal(final Element element) throws InvalidDataException
  {
    TransparentVcs.readRenamedElements( element, activitiesAssociations, PERSISTENCY_SAVED_ACTIVITY_MAP_TAG, false );

    List elements = element.getChildren( VIEW_INFO_TAG );
    for (Object cclObj : elements)
    {
      if (cclObj instanceof Element)
      {
        ViewInfo info = new ViewInfo();
        info.tag = ((Element)cclObj).getChild( TAG_TAG ).getValue();

        //  IDEADEV-19094. Can it be so that View may NOT have an UUID tag?
        //  Or we were fucked by the parsing output?
        if( ((Element)cclObj).getChild( UUID_TAG ) != null )
          info.uuid = ((Element)cclObj).getChild( UUID_TAG ).getValue();

        info.isUcm = Boolean.valueOf(((Element)cclObj).getChild( UCM_TAG ).getValue()).booleanValue();
        info.isSnapshot = Boolean.valueOf(((Element)cclObj).getChild(SNAPSHOT_TAG).getValue()).booleanValue();

        String root = ((Element)cclObj).getChild( CONTENT_ROOT_TAG ).getValue();
        viewsMapByRoot.put( root, info );
      }
    }
    extractViewActivities();
  }

  public void writeExternal( final Element element ) throws WriteExternalException
  {
    for( String root : viewsMapByRoot.keySet() )
    {
      final ViewInfo info = viewsMapByRoot.get( root );
      final Element listElement = new Element( VIEW_INFO_TAG );

      listElement.addContent( new Element( CONTENT_ROOT_TAG ).addContent( root ) );
      listElement.addContent( new Element( TAG_TAG ).addContent( info.tag ) );
      listElement.addContent( new Element( UUID_TAG ).addContent( info.uuid ) );
      listElement.addContent( new Element( UCM_TAG ).addContent( Boolean.toString( info.isUcm ) ) );
      listElement.addContent( new Element( SNAPSHOT_TAG ).addContent( Boolean.toString( info.isSnapshot ) ) );
      
      if( info.currentActivity != null )
        listElement.addContent( new Element( ACTIVITY_TAG ).addContent( info.currentActivity.name ) );

      element.addContent( listElement );
    }

    TransparentVcs.writePairedElement( element, activitiesAssociations, PERSISTENCY_SAVED_ACTIVITY_MAP_TAG );
  }
}
