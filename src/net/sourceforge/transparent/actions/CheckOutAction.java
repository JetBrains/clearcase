package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.TransparentVcs;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CheckOutAction extends SynchronousAction {
  @NonNls private static final String ACTION_NAME = "Check Out";
  @NonNls private static final String CHECKOUT_HIJACKED_TITLE = "Check out Hijacked File";
  @NonNls private static final String NOT_A_VOB_OBJECT_SIG = "Not a vob object";
  @NonNls private static final String IS_ALREADY_CHECKED_OUT_SIG = "is already checked out";
  private int cnt;

  @Override
  protected String getActionName(AnActionEvent e) {
    TransparentVcs host = getHost(e);
    boolean verbose = host != null && host.getCheckoutOptions() != null && host.getCheckoutOptions().getValue();
    return verbose ? ACTION_NAME + "..." : ACTION_NAME;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    TransparentVcs host = getHost(e);
    boolean isVisible = host != null && host.getConfig() != null;
    e.getPresentation().setVisible(isVisible);
    e.getPresentation().setEnabled(isVisible && !host.getConfig().isOffline() &&
                                   e.getPresentation().isEnabled());
  }

  @Override
  protected boolean isEnabled(VirtualFile file, final Project project) {
    if (!VcsUtil.isFileForVcs(file, project, TransparentVcs.getInstance(project))) {
      return false;
    }

    //  NB: if invoked for a folder, the status is most often "NOT_CHANGED"
    FileStatus status = getFileStatus(project, file);
    return status == FileStatus.NOT_CHANGED || status == FileStatus.HIJACKED;
  }

  @Override
  protected void execute(AnActionEvent e, final List<VcsException> errors) {
    cnt = 0;
    final Project project = e.getData(CommonDataKeys.PROJECT);
    String comment = "";
    final VirtualFile[] files = VcsUtil.getVirtualFiles(e);

    if (TransparentVcs.getInstance(project).getCheckoutOptions().getValue()) {
      CheckoutDialog dialog = files.length == 1 ?
                              new CheckoutDialog(project, files[0]) :
                              new CheckoutDialog(project, files);
      dialog.show();
      if (dialog.getExitCode() == DialogWrapper.CANCEL_EXIT_CODE) {
        return;
      }

      comment = dialog.getComment();
    }
    if (files.length == 0) return;
    final ProgressManager pm = ProgressManager.getInstance();
    final String finalComment = comment;
    String title = "Checkout ";
    if (files.length > 1) {
      title += "files";
    }
    else {
      title += files[0].isDirectory() ? "directory" : "file";
    }
    pm.runProcessWithProgressSynchronously(() -> {
      final ProgressIndicator indicator = pm.getProgressIndicator();
      indicator.setIndeterminate(true);
      for (VirtualFile file : files) {
        performOnFile(project, file, finalComment, errors, indicator);
      }
    }, title, true, project);
  }

  private void performOnFile(final Project project,
                             VirtualFile file,
                             String comment,
                             List<VcsException> errors,
                             ProgressIndicator indicator) {
    if (indicator != null) {
      indicator.checkCanceled();
      final VirtualFile parent = file.getParent();
      indicator.setText("Processing: " + file.getName() + " (" + (parent == null ? file.getPath() : parent.getPath()) + ')');
      indicator.setText2("Processed: " + cnt + " files");
      ++cnt;
    }
    if (isEnabled(file, project)) {
      VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
      try {
        perform(file, comment, project);
        mgr.fileDirty(file);
      }
      catch (VcsException ex) {
        if (!isIgnorableMessage(ex.getMessage())) {
          ex.setVirtualFile(file);
          errors.add(ex);
        }
      }
      catch (RuntimeException ex) {
        if (!isIgnorableMessage(ex.getMessage())) {
          VcsException vcsEx = new VcsException(ex);
          vcsEx.setVirtualFile(file);
          errors.add(vcsEx);
        }
      }
      executeRecursively(project, file, comment, errors, indicator);
    }
  }

  private void executeRecursively(final Project project,
                                  VirtualFile file,
                                  String comment,
                                  List<VcsException> errors,
                                  ProgressIndicator indicator) {
    if (file.isDirectory()) {
      for (VirtualFile child : file.getChildren()) {
        performOnFile(project, child, comment, errors, indicator);
      }
    }
  }

  protected static void perform(VirtualFile file, String comment, final Project project) throws VcsException {
    final TransparentVcs vcs = TransparentVcs.getInstance(project);
    //  Checkout command can be issued for a folder - we do not support this as
    //  the separate operation.
    if (file.isDirectory()) {
      vcs.folderCheckedOut(file.getPath());
    }

    FileStatus status = getFileStatus(project, file);
    if (status == FileStatus.UNKNOWN || status == FileStatus.MODIFIED) {
      return;
    }

    boolean keepHijack = false;
    if (status == FileStatus.HIJACKED) {
      keepHijack = askIfUseHijackedFileAsCheckedOut(file);
    }

    try {
      vcs.checkoutFile(file, keepHijack, comment);

      //  Assign the special marker to the file indicating that there is no need
      //  to run <cleartool> command on the file - it is known to be modified
      //  after the checkout command.
      file.putUserData(TransparentVcs.SUCCESSFUL_CHECKOUT, true);
      file.refresh(true, file.isDirectory());
    }
    catch (ClearCaseException exc) {
      VcsException vcsExc = new VcsException(exc);
      AbstractVcsHelper.getInstance(project).showError(vcsExc, ACTION_NAME);
    }
  }

  private static boolean askIfUseHijackedFileAsCheckedOut(@NotNull final VirtualFile file) {
    final Ref<Integer> answer = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      String message = "The file " + file.getPresentableUrl() + " has been hijacked. \n" +
                       "Would you like to use it as the checked-out file?\nIf not it will be lost.";
      answer.set(Messages.showYesNoDialog(message, CHECKOUT_HIJACKED_TITLE, Messages.getQuestionIcon()));
    });
    return answer.get() == Messages.YES;
  }

  private static boolean isIgnorableMessage(String message) {
    return message.contains(NOT_A_VOB_OBJECT_SIG) || message.contains(IS_ALREADY_CHECKED_OUT_SIG);
  }

  @Override
  protected void perform(VirtualFile file, final Project project) {
    //  We should never reach this point. Most methods are overloaded to support
    //  adding uniform data (here - comment) to the operation.
    throw new UnsupportedOperationException();
  }
}
