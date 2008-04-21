package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

public class VersionTreeAction extends AsynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Version Tree";

  public void perform( VirtualFile file, AnActionEvent e )
  {
    cleartool( "lsvtree", "-g", getVersionExtendedPathName( file ) );
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

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    if( !VcsUtil.isFileForVcs( file, _actionProjectInstance, TransparentVcs.getInstance(_actionProjectInstance) ) )
      return false;

    FileStatus status = getFileStatus( _actionProjectInstance, file );
    return status != FileStatus.ADDED && status != FileStatus.UNKNOWN &&
           status != FileStatus.IGNORED;
  }
}
