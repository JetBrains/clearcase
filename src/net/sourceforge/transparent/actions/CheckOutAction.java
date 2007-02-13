// Decompiled by Jad v1.5.8e. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/kpdus/jad.html
// Decompiler options: packimports(3) braces fieldsfirst splitstr(nl) nonlb space 
// Source File Name:   CheckOutAction.java

package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.Status;

// Referenced classes of package net.sourceforge.transparent.actions:
//            SynchronousAction, ActionContext

public class CheckOutAction extends SynchronousAction
{
  protected String getActionName() {  return "Check Out...";  }

  public void update( AnActionEvent e )
  {
    super.update( e );

    if ( getHost( e ).getTransparentConfig().offline )
      e.getPresentation().setEnabled( false );
  }

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    FileStatus status = getFileStatus( e.getData( DataKeys.PROJECT ), file );
    return status == FileStatus.NOT_CHANGED || status == FileStatus.HIJACKED;
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    boolean keepHijack = false;

    Status fileStatus = getHost( e ).getFileStatus( file );
    if ( fileStatus == Status.HIJACKED )
    {
      String message = "The file " + file.getPresentableUrl() + " has been hijacked. \n" +
                       "Would you like to use it as the checked-out file?\n" + "  If not it will be lost.";
      int answer = Messages.showYesNoDialog( message, "Checkout hijacked file", Messages.getQuestionIcon() );
      keepHijack = answer == 0;
    }

    getHost( e ).checkoutFile( file, keepHijack );
  }
}
