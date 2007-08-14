package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
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

    boolean isVisible = (getHost( e ) != null && getHost( e ).getConfig() != null);
    e.getPresentation().setVisible( isVisible );
    e.getPresentation().setEnabled( isVisible && getHost( e ).getConfig().isOffline );
  }

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    CCaseConfig vcsConfig = CCaseConfig.getInstance( _actionProjectInstance );
    FileStatus status = getFileStatus( _actionProjectInstance, file );
    return !file.isWritable() && (status != FileStatus.UNKNOWN) && !vcsConfig.isViewDynamic(); 
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    CCaseEditFileProvider.hijackFile( file );
  }
}
