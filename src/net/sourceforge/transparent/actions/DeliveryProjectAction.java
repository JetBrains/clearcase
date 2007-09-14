package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
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

  public void perform( VirtualFile file, AnActionEvent e )
  {
    TransparentVcs host = TransparentVcs.getInstance( getProject( e ) );
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( getProject( e ) );
    VirtualFile firstRoot = mgr.getRootsUnderVcs(host)[ 0 ];
    if( firstRoot != null )
    {
      String path = VcsUtil.getCanonicalPath( firstRoot.getPath() );
      TransparentVcs.cleartoolOnLocalPath( path, "deliver", "-g" );
    }
  }

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    boolean status = super.isEnabled( file, e );
    status &= allViewsAreUcm( getHost( e ) );
    return status;
  }

  private static boolean allViewsAreUcm( TransparentVcs host )
  {
    boolean isUcm = true;
    for( TransparentVcs.ViewInfo info : host.viewsMap.values() )
    {
      isUcm &= info.isUcm;
    }
    return isUcm;
  }

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }
}
