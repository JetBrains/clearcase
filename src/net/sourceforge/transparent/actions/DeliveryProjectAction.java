package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.CCaseViewsManager;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Apr 23, 2007
 */
public class DeliveryProjectAction extends AsynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Deliver Activities";
  
  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }

  public void perform(VirtualFile file, final Project project)
  {
    CCaseViewsManager viewsMgr = CCaseViewsManager.getInstance( project );
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );

    VirtualFile[] roots = mgr.getRootsUnderVcs(TransparentVcs.getInstance(project));
    for( VirtualFile root : roots )
    {
      if( viewsMgr.isUcmViewForFile( root ) )
      {
        String path = VcsUtil.getCanonicalPath( root.getPath() );
        TransparentVcs.cleartoolOnLocalPath( path, "deliver", "-g" );
        break;
      }
    }
  }

  protected boolean isEnabled(VirtualFile file, final Project project)
  {
    boolean status = super.isEnabled( file, project);
    status &= CCaseViewsManager.getInstance( project ).isAnyUcmView();
    return status;
  }
}
