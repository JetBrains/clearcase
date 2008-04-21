package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

public class FindProjectCheckoutsAction extends AsynchronousAction
{
  @NonNls private final static String WARNING_TEXT = "In graphical mode only one content root as a VOB object can be shown.";
  @NonNls private final static String WARNING_TITLE = "Show One VOB Path";

  @NonNls private final static String ACTION_NAME = "Find Project Checkouts";

  public void perform(VirtualFile file, final Project project)
  {
    TransparentVcs host = TransparentVcs.getInstance( project );
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
    VirtualFile[] roots = mgr.getRootsUnderVcs( host );
    if( roots.length > 1 )
    {
      Messages.showWarningDialog( project, WARNING_TEXT, WARNING_TITLE );
    }

    cleartool( "lscheckout", "-g", roots[ 0 ].getPath() );
  }

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }
}
