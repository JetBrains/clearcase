package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

public class CheckOutAction extends SynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Check Out...";
  
  protected String getActionName() {  return ACTION_NAME;  }

  public void update( AnActionEvent e )
  {
    super.update( e );

    if ( getHost( e ).getConfig().offline )
      e.getPresentation().setEnabled( false );
  }

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    FileStatus status = getFileStatus( e.getData( DataKeys.PROJECT ), file );

    //  NB: if invoked for a folder, the status is most often "NOT_CHANGED"
    return status == FileStatus.NOT_CHANGED || status == FileStatus.HIJACKED;
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    /**
     * Take into account that Checkout command can be issued for a folder.
     * So we need additionally check the state of the each file in particular
     * in the <perform> method.
     */
    FileStatus status = getFileStatus( e.getData( DataKeys.PROJECT ), file );
    if( status == FileStatus.UNKNOWN || status == FileStatus.MODIFIED )
      return;

    boolean keepHijack = false;
    if( status == FileStatus.HIJACKED )
    {
      String message = "The file " + file.getPresentableUrl() + " has been hijacked. \n" +
                       "Would you like to use it as the checked-out file?\n" + "  If not it will be lost.";
      int answer = Messages.showYesNoDialog( message, "Checkout hijacked file", Messages.getQuestionIcon() );
      keepHijack = (answer == 0);
    }

    getHost( e ).checkoutFile( file, keepHijack );
  }
}
