// Decompiled by Jad v1.5.8e. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/kpdus/jad.html
// Decompiler options: packimports(3) braces fieldsfirst splitstr(nl) nonlb space 
// Source File Name:   UndoCheckOutAction.java

package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;

// Referenced classes of package net.sourceforge.transparent.actions:
//            SynchronousAction, ActionContext

public class UndoCheckOutAction extends SynchronousAction
{
  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    return getFileStatus( e.getData( DataKeys.PROJECT ), file ) == FileStatus.MODIFIED;
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    getHost( e ).undoCheckoutFile( file.getPath() );
  }

  protected String getActionName() {  return "Undo Checkout";   }
}
