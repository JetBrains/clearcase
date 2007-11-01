package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.CCaseViewsManager;
import net.sourceforge.transparent.ChangeManagement.CCaseChangeProvider;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: May 7, 2007
 */
public class SynchActivitiesAction extends SynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Synchronize Activities";

  public void update( AnActionEvent e )
  {
    super.update( e );

    boolean enabled = CCaseViewsManager.getInstance( getProject( e ) ).isAnyUcmView();
    e.getPresentation().setEnabled( enabled );
  }

  protected void execute( AnActionEvent e, List<VcsException> errors )
  {
    try {  perform( null, e ); }
    catch( VcsException exc ) { errors.add( exc ); }
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    CCaseViewsManager.getInstance( getProject(e) ).extractViewActivities();

    //  Convert current activity of each view into ChangeList
    reloadCurrentActivities2Changelists( e );

    //  For each file with "MODIFIED" status reread its mapped activity
    //  (via "describe" command), and if its activity differs from the name
    //  of its current change list, move it to the new change list.
    relocateChangedFiles( e );
    
    //  New files (with status "ADDED") should be relocated to the newly
    //  activated activities.
    relocateNewFiles( e );
  }

  private static void reloadCurrentActivities2Changelists( AnActionEvent e )
  {
    CCaseViewsManager viewsManager = CCaseViewsManager.getInstance( getProject(e) );
    ChangeListManager changesMgr = ChangeListManager.getInstance( getProject(e) );
    for( CCaseViewsManager.ViewInfo info : viewsManager.viewsMapByRoot.values() )
    {
      if( info.currentActivity != null )
      {
        if( changesMgr.findChangeList( info.currentActivity.publicName ) == null )
        {
          changesMgr.addChangeList( info.currentActivity.publicName, null );
        }
      }
    }
  }

  private static void relocateChangedFiles( AnActionEvent e )
  {
    TransparentVcs host = getHost( e );
    CCaseViewsManager viewsManager = CCaseViewsManager.getInstance( getProject(e) );
    ChangeListManager mgr = ChangeListManager.getInstance( getProject(e) );
    List<VirtualFile> files = mgr.getAffectedFiles();
    List<String> files2Analyze = new ArrayList<String>();

    for( VirtualFile vfile : files )
    {
      if( mgr.getStatus( vfile ) == FileStatus.MODIFIED && host.fileIsUnderVcs( vfile.getPath() ))
        files2Analyze.add( vfile.getPath() );
    }

    final CCaseChangeProvider changeProvider = (CCaseChangeProvider)host.getChangeProvider();
    assert changeProvider != null;

    changeProvider.setActivityInfoOnChangedFiles( files2Analyze );

    for( VirtualFile vfile : files )
    {
      if( mgr.getStatus( vfile ) == FileStatus.MODIFIED && host.fileIsUnderVcs( vfile.getPath() ))
      {
        Change change = mgr.getChange( vfile );
        LocalChangeList list = mgr.getChangeList( change );
        String hostedActivity = viewsManager.getCheckoutActivityForFile( vfile.getPath() );
        if( hostedActivity != null && !hostedActivity.equals( list.getName() ) )
        {
          mgr.moveChangesTo( mgr.findChangeList( hostedActivity ), new Change[]{ change } );
        }
      }
    }
  }

  private static void relocateNewFiles( AnActionEvent e )
  {
    Project project = getProject( e );
    ChangeListManager changesMgr = ChangeListManager.getInstance( project );
    ProjectLevelVcsManager pmgr = ProjectLevelVcsManager.getInstance( project );
    CCaseViewsManager viewsManager = CCaseViewsManager.getInstance( project );

    List<VirtualFile> files = changesMgr.getAffectedFiles();
    for( VirtualFile vfile : files )
    {
      FileStatus status = changesMgr.getStatus( vfile );
      if( status == FileStatus.ADDED )
      {
        VirtualFile root = pmgr.getVcsRootFor( vfile );
        CCaseViewsManager.ViewInfo info = viewsManager.getViewByRoot( root );
        String currentActivityName = (info.currentActivity != null) ? info.currentActivity.publicName : null;
        if( info != null )
        {
          Change change = changesMgr.getChange( vfile );
          LocalChangeList list = changesMgr.getChangeList( change );
          if( currentActivityName != null && !list.getName().equals( currentActivityName ) )
          {
            viewsManager.addFile2Changelist( vfile.getPath(), currentActivityName );
            changesMgr.moveChangesTo( changesMgr.findChangeList( currentActivityName ), new Change[]{ change } );
          }
        }
      }
    }
  }

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }
}
