package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.TransparentVcs;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;

public abstract class FileAction extends VcsAction {
  public void update(AnActionEvent e) {
    super.update(e);

    Project project = e.getData(PlatformDataKeys.PROJECT);
    VirtualFile[] files = VcsUtil.getVirtualFiles(e);
    boolean enabled = (project != null && files.length > 0);

    for (VirtualFile file : files) {
      enabled &= isEnabled(file, project);
    }

    e.getPresentation().setEnabled(enabled);
  }

  protected boolean isEnabled(VirtualFile file, final Project project) {
    return getFileStatus(project, file) != FileStatus.ADDED;
  }

  public static void cleartool(@NonNls String... subcmd) throws ClearCaseException {
    TransparentVcs.cleartool(subcmd);
  }
}
