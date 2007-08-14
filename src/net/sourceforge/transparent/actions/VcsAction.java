package net.sourceforge.transparent.actions;

import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
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

  protected Project _actionProjectInstance;
  protected TransparentVcs _hostInstance;

  public void update(AnActionEvent e)
  {
    String actionName = getActionName(e);
    if (actionName == null) throw new IllegalStateException("Internal error - Action Name is NULL.");

    e.getPresentation().setText(actionName);
    _actionProjectInstance = getProject( e );
  }

  protected abstract String getActionName(AnActionEvent e);

  protected void execute(AnActionEvent e, List<VcsException> errors) {
  }

  public void actionPerformed(AnActionEvent e)
  {
    _actionProjectInstance = e.getData(DataKeys.PROJECT);
    _hostInstance = getHost( e );
    
    FileDocumentManager.getInstance().saveAllDocuments();
    List<VcsException> errors = runAction(e);

    if (errors.size() > 0) {
      @NonNls final String title = getActionName(e) + " failed";
      AbstractVcsHelper.getInstance( _actionProjectInstance ).showErrors(errors, title);
      Messages.showErrorDialog( _actionProjectInstance, OPERATION_FAILED_TEXT, title );
    }
  }

  protected List<VcsException> runAction(AnActionEvent e)
  {
    List<VcsException> list = new ArrayList<VcsException>();

    AbstractVcsHelper helper = AbstractVcsHelper.getInstance(e.getData(DataKeys.PROJECT));
    LocalHistoryAction a = helper.startLocalHistoryAction(e.getPresentation().getText());

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
    return e.getData(DataKeys.PROJECT);
  }

  protected static TransparentVcs getHost(AnActionEvent e) {
    return TransparentVcs.getInstance(e.getData(DataKeys.PROJECT));
  }
}
