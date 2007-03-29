package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsException;
import net.sourceforge.transparent.Runner;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class MergeProjectAction extends VcsAction
{
  @NonNls private final static String ACTION_NAME = "Merge Project";
  @NonNls private final static String CLEARTOOL_CMD = "clearmrgman";
  
  protected void execute( AnActionEvent ct, List<VcsException> errors )
  {
    (new Runner()).run( CLEARTOOL_CMD );
  }

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }
}
