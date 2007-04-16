package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Apr 5, 2007
 */
public class MergeAction extends AsynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Merge file...";

  public void perform( VirtualFile file, AnActionEvent e )
  {
    cleartool( "merge", "-g", "-to", file.getPath(), "-version", "/main/LATEST" );
  }

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    FileStatus status = getFileStatus( e.getData( DataKeys.PROJECT ), file );
    return status == FileStatus.MERGED_WITH_CONFLICTS;
  }

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }
}

