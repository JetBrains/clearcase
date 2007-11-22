package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.CCaseEditFileProvider;
import net.sourceforge.transparent.CCaseViewsManager;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

public class HijackAction extends SynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Hijack File";

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }

  public void update( AnActionEvent e )
  {
    super.update( e );
    boolean isEnabled = e.getPresentation().isEnabled();

    TransparentVcs host = getHost( e );
    boolean isVisible = (host != null && host.getConfig() != null);
    e.getPresentation().setVisible( isVisible );
    e.getPresentation().setEnabled( isVisible && isEnabled && host.getConfig().isOffline );
  }

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    CCaseViewsManager mgr = CCaseViewsManager.getInstance( _actionProjectInstance );
    CCaseViewsManager.ViewInfo view = mgr.getViewByFile( file );

    FileStatus status = getFileStatus( _actionProjectInstance, file );
    return !file.isWritable() && (status != FileStatus.UNKNOWN) &&
           (view != null) && view.isSnapshot; 
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    CCaseEditFileProvider.hijackFile( file );
  }
}
