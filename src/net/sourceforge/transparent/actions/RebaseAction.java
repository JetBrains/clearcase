package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

public class RebaseAction extends AsynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Rebase Project";

  public void perform( VirtualFile file, AnActionEvent e )
  {
    cleartool( "rebase", "-g" );
  }

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }
}
