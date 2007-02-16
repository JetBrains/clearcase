// Decompiled by Jad v1.5.8e. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/kpdus/jad.html
// Decompiler options: packimports(3) braces fieldsfirst splitstr(nl) nonlb space 
// Source File Name:   HijackAction.java

package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.CCaseEditFileProvider;

// Referenced classes of package net.sourceforge.transparent.actions:
//            SynchronousAction, ActionContext

public class HijackAction extends SynchronousAction
{
  protected String getActionName() {  return "Hijack File";  }

  public void update( AnActionEvent e )
  {
    super.update( e );

    if ( !getHost( e ).getConfig().offline )
      e.getPresentation().setEnabled( false );
  }

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    FileStatus status = getFileStatus( e.getData( DataKeys.PROJECT ), file );
    return !file.isWritable() && (status != FileStatus.UNKNOWN);
  }

  protected void perform( VirtualFile file, AnActionEvent e ) throws VcsException
  {
    CCaseEditFileProvider.hijackFile( file );
//      (new CheckOutHelper( getHost( e ) )).hijackFile( file );
  }
}
