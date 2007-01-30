// Decompiled by Jad v1.5.8e. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/kpdus/jad.html
// Decompiler options: packimports(3) braces fieldsfirst splitstr(nl) nonlb space 
// Source File Name:   FileAction.java

package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.ClearCaseException;
import net.sourceforge.transparent.Runner;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

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
    return getFileStatus( e.getData( DataKeys.PROJECT ), file ) != FileStatus.ADDED;
  }

  @NonNls
  public static void cleartool( @NonNls String... subcmd ) throws ClearCaseException
  {
    try {
      (new Runner()).runAsynchronously(Runner.getCommand("cleartool", subcmd));
    }
    catch (IOException e) {
      LOG.error(e);
      throw new ClearCaseException( e.getMessage() );
    }
  }
}
