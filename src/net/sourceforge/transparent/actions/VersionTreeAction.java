package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;

public class VersionTreeAction extends AsynchronousAction
{
  public void perform( VirtualFile file, AnActionEvent e ) {
    cleartool("lsvtree", "-g", getVersionExtendedPathName( file, e ));
  }

  protected String getActionName() {  return "Version Tree";  }
}
