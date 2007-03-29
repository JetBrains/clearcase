package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

public class VersionTreeAction extends AsynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Version Tree";

  public void perform( VirtualFile file, AnActionEvent e ) {
    cleartool( "lsvtree", "-g", getVersionExtendedPathName( file, e ) );
  }

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }
}
