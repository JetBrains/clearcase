package net.sourceforge.transparent.History;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.ChangeManagement.CCaseContentRevision;
import net.sourceforge.transparent.StatusMultipleProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * @author irengrig
 *         Date: 3/28/11
 *         Time: 1:55 PM
 */
public class CCaseDiffProvider implements DiffProvider {
  @NotNull private final Project myProject;

  public CCaseDiffProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    final String currentRevision = StatusMultipleProcessor.getCurrentRevision(file.getPath());
    return currentRevision == null ? VcsRevisionNumber.NULL : new CCaseRevisionNumber(currentRevision, 0);
  }

  @Override
  public ItemLatestState getLastRevision(VirtualFile virtualFile) {
    final String[] result = new String[1];
    try {
      CCaseHistoryProvider.historyGetter(myProject, VcsUtil.getFilePath(virtualFile), 1, new Consumer<CCaseHistoryParser.SubmissionData>() {
        @Override
        public void consume(CCaseHistoryParser.SubmissionData submissionData) {
          if (submissionData != null) {
            result[0] = submissionData.version;
          }
        }
      });
    }
    catch (VcsException e) {
      AbstractVcsHelper.getInstance(myProject).showError(e,  "Get last revision of " + virtualFile.getPath() + " failed.");
      return new ItemLatestState(VcsRevisionNumber.NULL, false, false);
    }
    return new ItemLatestState(new CCaseRevisionNumber(result[0], 0), true, false);
  }

  @Override
  public ItemLatestState getLastRevision(FilePath filePath) {
    VirtualFile vf = filePath.getVirtualFile();
    if (vf == null) {
      vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(filePath.getIOFile());
    }
    if (vf == null) {
      return new ItemLatestState(VcsRevisionNumber.NULL, false, false);
    }
    return getLastRevision(vf);
  }

  @Override
  public ContentRevision createFileContent(VcsRevisionNumber revisionNumber, VirtualFile selectedFile) {
    return new CCaseContentRevision(VcsUtil.getFilePath(selectedFile), myProject, revisionNumber.asString());
  }

  @Override
  public VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot) {
    return null;
  }
}
