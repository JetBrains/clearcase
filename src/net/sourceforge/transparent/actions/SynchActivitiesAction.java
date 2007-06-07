package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.ChangeManagement.CCaseChangeProvider;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

import java.io.File;
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

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    boolean status = true;
    TransparentVcs host = getHost( e );

    for( TransparentVcs.ViewInfo info : host.viewsMap.values() )
      status &= info.isUcm;

    return status;
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    TransparentVcs host = getHost( e );
    host.extractViewActivities();

    //  Find out a current activity for each view
    rereadCurrentActivities( e );

    //  For each file with "MODIFIED" status reread its mapped activity
    //  (via "describe" command), and if its activity differs from the name
    //  of its current change list, move it to the new change list.
    relocateChangedFiles( e );
    
    //  New files (with status "ADDED") should be relocated to the newly
    //  activated activities.
    relocateNewFiles( e );
  }

  private static void rereadCurrentActivities( AnActionEvent e )
  {
    TransparentVcs host = getHost( e );
    ChangeListManager mgr = ChangeListManager.getInstance( getProject(e) );
    for( TransparentVcs.ViewInfo info : host.viewsMap.values() )
    {
      if( mgr.findChangeList( info.activityName ) == null )
      {
        mgr.addChangeList( info.activityName, null );
      }
    }
  }

  private static void relocateChangedFiles( AnActionEvent e )
  {
    TransparentVcs host = getHost( e );
    ChangeListManager mgr = ChangeListManager.getInstance( getProject(e) );
    List<VirtualFile> files = mgr.getAffectedFiles();
    List<String> files2Analyze = new ArrayList<String>();

    for( VirtualFile vfile : files )
    {
      if( mgr.getStatus( vfile ) == FileStatus.MODIFIED && host.fileIsUnderVcs( vfile.getPath() ))
        files2Analyze.add( vfile.getPath() );
    }

    CCaseChangeProvider.setActivityInfoOnChangedFiles( files2Analyze );

    for( VirtualFile vfile : files )
    {
      if( mgr.getStatus( vfile ) == FileStatus.MODIFIED && host.fileIsUnderVcs( vfile.getPath() ))
      {
        Change change = mgr.getChange( vfile );
        LocalChangeList list = mgr.getChangeList( change );
        String hostedActivity = host.getCheckoutActivityForFile( vfile.getPath() );
        if( hostedActivity != null && !hostedActivity.equals( list.getName() ) )
        {
          mgr.moveChangesTo( mgr.findChangeList( hostedActivity ), new Change[]{ change } );
        }
      }
    }
  }

  private static void relocateNewFiles( AnActionEvent e )
  {
    TransparentVcs host = getHost( e );
    ChangeListManager mgr = ChangeListManager.getInstance( getProject(e) );
    ProjectLevelVcsManager pmgr = ProjectLevelVcsManager.getInstance( getProject(e) );

    List<VirtualFile> files = mgr.getAffectedFiles();
    for( VirtualFile vfile : files )
    {
      FileStatus status = mgr.getStatus( vfile );
      if( status == FileStatus.ADDED )
      {
        VirtualFile root = pmgr.getVcsRootFor( vfile );
        TransparentVcs.ViewInfo info = host.viewsMap.get( root.getPath() );
        Change change = mgr.getChange( vfile );
        LocalChangeList list = mgr.getChangeList( change );

        if( !list.getName().equals( info.activityName ) )
        {
          host.addFile2Changelist( new File( vfile.getPath() ), info.activityName );
          mgr.moveChangesTo( mgr.findChangeList( info.activityName ), new Change[]{ change } );
        }
      }
    }
  }

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }
}
