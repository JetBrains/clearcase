package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.exceptions.ClearCaseException;

import java.util.ArrayList;
import java.util.List;

public abstract class AsynchronousAction extends FileAction
{
  protected abstract void perform(VirtualFile virtualfile, final Project project) throws VcsException;

  public void update( AnActionEvent e )
  {
    super.update( e );
    VirtualFile[] files = VcsUtil.getVirtualFiles( e );

    if( e.getPresentation().isEnabled())
      e.getPresentation().setEnabled( files.length == 1 );
  }

  protected List<VcsException> runAction( AnActionEvent e )
  {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    VirtualFile file = VcsUtil.getOneVirtualFile( e );
    List<VcsException> exceptions = new ArrayList<VcsException>();
    try {
        perform( file, project);
    }
    catch( VcsException ex ) {
        ex.setVirtualFile( file );
        exceptions.add( ex );
    }
    catch ( ClearCaseException ex ) {
        VcsException vcsEx = new VcsException( ex );
        vcsEx.setVirtualFile( file );
        exceptions.add( vcsEx );
    }
    return exceptions;
  }

  public static String getVersionExtendedPathName(final Project project, VirtualFile file)
  {
    String path = VcsUtil.getCanonicalPath( file.getPath() );
    FileStatus status = getFileStatus( project, file );
    return status.equals( FileStatus.HIJACKED ) ? path + "@@" : path;
  }
}
