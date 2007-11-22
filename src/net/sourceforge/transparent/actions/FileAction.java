package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.TransparentVcs;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;

public abstract class FileAction extends VcsAction
{
  public void update( AnActionEvent e )
  {
    super.update( e );

    VirtualFile[] files = VcsUtil.getVirtualFiles( e );
    boolean enabled = (files.length > 0);
    
    for( VirtualFile file : files )
      enabled &= isEnabled( file, e );

    e.getPresentation().setEnabled( enabled );
  }

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    return getFileStatus( getProject( e ), file ) != FileStatus.ADDED;
  }

  public static void cleartool( @NonNls String... subcmd ) throws ClearCaseException
  {
    TransparentVcs.cleartool( subcmd );
  }
}
