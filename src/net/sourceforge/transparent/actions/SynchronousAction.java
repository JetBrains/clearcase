package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;

import java.util.List;

public abstract class SynchronousAction extends FileAction
{
  protected List<VcsException> runAction( AnActionEvent e )
  {
//      e.isActionRecursive = isActionRecursive( e );
      return super.runAction( e );
  }

  protected void execute( AnActionEvent e, List<VcsException> errors )
  {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    VirtualFile[] files = VcsUtil.getVirtualFiles( e );
    for( VirtualFile file : files )
    {
      if( isEnabled( file, project) )
        executeOnFile(file, errors, project);
    }
  }

  protected void executeOnFile(VirtualFile file, List<VcsException> errors, final Project project)
  {
    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance( project );

    try
    {
      perform( file, project);
      mgr.fileDirty( file );
    }
    catch( VcsException ex ) {
      ex.setVirtualFile( file );
      errors.add( ex );
    }
    catch ( RuntimeException ex ) {
      VcsException vcsEx = new VcsException( ex );
      vcsEx.setVirtualFile( file );
      errors.add( vcsEx );
    }
    handleRecursiveExecute(file, errors, project);
  }

  private void handleRecursiveExecute(VirtualFile file, List<VcsException> errors, final Project project)
  {
//    if (file.isDirectory() && context.isActionRecursive) {
    if( file.isDirectory() )
    {
      for( VirtualFile child : file.getChildren() )
          executeOnFile(child, errors, project);
    }
  }

  protected abstract void perform(VirtualFile virtualfile, final Project project) throws VcsException;
}
