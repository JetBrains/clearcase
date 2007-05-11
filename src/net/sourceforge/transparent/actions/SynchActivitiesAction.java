package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.CCaseConfig;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

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
    CCaseConfig config = CCaseConfig.getInstance( getProject( e ) );
    return config.isUcmView();
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    TransparentVcs host = getHost( e );
    host.extractViewActivities();

    ChangeListManager mgr = ChangeListManager.getInstance( getProject(e) );
    for( TransparentVcs.ViewInfo info : host.viewsMap.values() )
    {
      if( mgr.findChangeList( info.activityName ) == null )
      {
        mgr.addChangeList( info.activityName, null );
      }
    }
  }

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }
}
