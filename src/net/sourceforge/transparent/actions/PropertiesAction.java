package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;

public class PropertiesAction extends AsynchronousAction
{
  public void perform( VirtualFile file, AnActionEvent e ) {
    cleartool("describe", "-g", file.getPath());
  }

  protected String getActionName() {  return "Properties";  }
}
