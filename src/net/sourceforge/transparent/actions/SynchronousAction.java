package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
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

  /*
  protected boolean isActionRecursive(ActionContext context) {
      for (VirtualFile file : context.files) {
          if (file.isDirectory()) {
              return askIfShouldRecurse(context);
          }
      }

      return false;
  }

  protected boolean askIfShouldRecurse(ActionContext context) {
      int r = Messages.showYesNoDialog(context.vcs.getProject(), "Should the action be recursive", "Recursive Action Question", Messages.getQuestionIcon());
      return r == 0;
  }
  */
  
  protected void execute( AnActionEvent e, List<VcsException> errors )
  {
    VirtualFile[] files = VcsUtil.getVirtualFiles( e );
    for( VirtualFile file : files )
    {
      if( isEnabled( file, e ) )
        executeOnFile( e, file, errors );
    }
  }

  protected void executeOnFile( AnActionEvent e, VirtualFile file, List<VcsException> errors )
  {
    Project project = e.getData( DataKeys.PROJECT );
    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance( project );

    try
    {
      perform( file, e );
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
    handleRecursiveExecute( e, file, errors );
  }

  private void handleRecursiveExecute( AnActionEvent e, VirtualFile file, List<VcsException> errors )
  {
//    if (file.isDirectory() && context.isActionRecursive) {
    if( file.isDirectory() )
    {
      for( VirtualFile child : file.getChildren() )
          executeOnFile( e, child, errors );
    }
  }

  protected abstract void perform( VirtualFile virtualfile, AnActionEvent e ) throws VcsException;
}
