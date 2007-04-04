package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

public class FindProjectCheckoutsAction extends FindCheckoutsAction
{
  @NonNls private final static String WARNING_TEXT = "In graphical mode only one content root as a VOB object can be shown.";
  @NonNls private final static String WARNING_TITLE = "Show One VOB Path";

  public void perform( VirtualFile file, AnActionEvent e )
  {
    TransparentVcs host = TransparentVcs.getInstance( getProject( e ) );
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( getProject( e ) );
    VirtualFile[] roots = mgr.getRootsUnderVcs( host );
    if( roots.length > 1 )
    {
      Messages.showWarningDialog( getProject( e ), WARNING_TEXT, WARNING_TITLE );
    }

    cleartool( "lscheckout", "-g", roots[ 0 ].getPath() );
  }
}
