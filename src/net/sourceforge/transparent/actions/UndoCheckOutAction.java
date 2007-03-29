package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public class UndoCheckOutAction extends SynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Undo Checkout";

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    Project p = e.getData( DataKeys.PROJECT );
    return file.isDirectory() || getFileStatus( p, file ) == FileStatus.MODIFIED;
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    FileStatus status = getFileStatus( e.getData( DataKeys.PROJECT ), file );
    
    if( !file.isDirectory() && (status == FileStatus.MODIFIED))
    {
      getHost( e ).undoCheckoutFile( file, errors );
    }

    if( errors.size() > 0 )
      throw errors.get( 0 );
  }

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }
}
