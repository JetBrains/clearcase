package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

public class PropertiesAction extends AsynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Properties";

  public void perform( VirtualFile file, AnActionEvent e ) {
    cleartool( "describe", "-g", file.getPath() );
  }

  protected String getActionName() {  return ACTION_NAME;  }
}
