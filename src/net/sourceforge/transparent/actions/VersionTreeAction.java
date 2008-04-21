package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

public class VersionTreeAction extends AsynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Version Tree";

  public void perform(VirtualFile file, final Project project)
  {
    cleartool( "lsvtree", "-g", getVersionExtendedPathName(project, file ) );
  }

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }

  public void update( AnActionEvent e )
  {
    super.update( e );

    TransparentVcs host = getHost( e );
    boolean isVisible = (host != null && host.getConfig() != null);
    e.getPresentation().setVisible( isVisible );
    e.getPresentation().setEnabled( isVisible && !host.getConfig().isOffline &&
                                    e.getPresentation().isEnabled() );
  }

  protected boolean isEnabled(VirtualFile file, final Project project)
  {
    if( !VcsUtil.isFileForVcs( file, project, TransparentVcs.getInstance(project) ) )
      return false;

    FileStatus status = getFileStatus( project, file );
    return status != FileStatus.ADDED && status != FileStatus.UNKNOWN &&
           status != FileStatus.IGNORED;
  }
}
