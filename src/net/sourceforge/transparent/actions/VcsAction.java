package net.sourceforge.transparent.actions;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public abstract class VcsAction extends AnAction
{
  @NonNls private static final String OPERATION_FAILED_TEXT = "One or more errors occured during operation";

  public void update(AnActionEvent e)
  {
    String actionName = getActionName(e);
    if (actionName == null) throw new IllegalStateException("Internal error - Action Name is NULL.");

    e.getPresentation().setText(actionName);
  }

  protected abstract String getActionName(AnActionEvent e);

  protected void execute(AnActionEvent e, List<VcsException> errors) {
  }

  public void actionPerformed(AnActionEvent e)
  {
    Project project = e.getData(PlatformDataKeys.PROJECT);

    FileDocumentManager.getInstance().saveAllDocuments();
    List<VcsException> errors = runAction(e);

    if (errors.size() > 0) {
      @NonNls final String title = getActionName(e) + " failed";
      AbstractVcsHelper.getInstance( project ).showErrors(errors, title);
      Messages.showErrorDialog( project, OPERATION_FAILED_TEXT, title );
    }
  }

  protected List<VcsException> runAction(AnActionEvent e)
  {
    List<VcsException> list = new ArrayList<VcsException>();

    LocalHistoryAction a = LocalHistory.startAction(getProject(e), e.getPresentation().getText());

    try {
      execute(e, list);
    }
    finally {
      a.finish();
    }

    return list;
  }

  protected static FileStatus getFileStatus(Project project, VirtualFile file) {
    ChangeListManager mgr = ChangeListManager.getInstance(project);
    return mgr.getStatus(file);
  }

  protected static Project getProject(AnActionEvent e) {
    return e.getData(PlatformDataKeys.PROJECT);
  }

  protected static TransparentVcs getHost(AnActionEvent e) {
    return TransparentVcs.getInstance(e.getData(PlatformDataKeys.PROJECT));
  }
}
