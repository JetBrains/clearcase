package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.CCaseConfig;
import net.sourceforge.transparent.CCaseEditFileProvider;
import org.jetbrains.annotations.NonNls;

public class HijackAction extends SynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Hijack File";

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }

  public void update( AnActionEvent e )
  {
    super.update( e );

    if ( !getHost( e ).getConfig().isOffline)
      e.getPresentation().setEnabled( false );
  }

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    Project project = e.getData( DataKeys.PROJECT );
    CCaseConfig vcsConfig = CCaseConfig.getInstance( project );
    FileStatus status = getFileStatus( project, file );
    return !file.isWritable() && (status != FileStatus.UNKNOWN) && !vcsConfig.isViewDynamic(); 
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    CCaseEditFileProvider.hijackFile( file );
  }
}
