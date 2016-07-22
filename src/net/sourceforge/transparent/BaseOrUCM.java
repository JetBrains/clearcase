package net.sourceforge.transparent;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class BaseOrUCM {
  @NonNls private static final String NOT_A_UCM_VOB = "cleartool: Error: Not a UCM PVOB:";
  @NonNls private final static String UCM_MISMATCH_GROUP = "UCM_MISMATCH_GROUP";

  private final TransparentVcs myVcs;
  private final AtomicReference<MyNotification> myCurrentNotification;

  public BaseOrUCM(final TransparentVcs vcs) {
    myVcs = vcs;
    myCurrentNotification = new AtomicReference<>(null);
  }

  public void checkRootsForUCMMismatch() {
    Boolean isUCM = isUCMByRoots();
    if (isUCM == null) {
      final MyNotification oldNotification = myCurrentNotification.getAndSet(null);
      if (oldNotification != null) {
        oldNotification.expire();
      }
      return;
    }

    final CCaseSharedConfig sharedConfig = CCaseSharedConfig.getInstance(myVcs.getProject());
    final boolean useUcmModelConfig = sharedConfig.isUseUcmModel();

    MyNotification newNotification = null;
    if (useUcmModelConfig && (! isUCM)) {
      newNotification = new MyNotification("Project is configured to use UCM model, but it is base ClearCase");
    } else if ((! useUcmModelConfig) && isUCM) {
      newNotification = new MyNotification("Project is configured as base ClearCase, but UCM model is used");
    }
    final MyNotification oldNotification = myCurrentNotification.getAndSet(newNotification);

    if (oldNotification != null) {
      oldNotification.expire();
    }
    if (newNotification != null) {
      Notifications.Bus.notify(newNotification, myVcs.getProject());
    }
  }

  @Nullable
  public Boolean isUCMByRoots() {
    Boolean UCM = null;
    final Project project = myVcs.getProject();
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    final VirtualFile[] roots = vcsManager.getRootsUnderVcs(myVcs);
    for (VirtualFile root : roots) {
      try {
        UCM = isUCM(root);
        break;
      } catch (ClearCaseException e) {
        //
      }
    }
    return UCM;
  }

  public static boolean isUCM(final VirtualFile vf) {
    final String result = TransparentVcs.cleartoolOnLocalPathWithOutput(vf.getPath(), "lsproj", "-s", "-cview");
    return (! StringUtil.isEmptyOrSpaces(result)) && (! result.startsWith(NOT_A_UCM_VOB));
  }

  private static class MyNotification extends Notification {
    private MyNotification(final @NotNull String content) {
      super(UCM_MISMATCH_GROUP, "ClearCase settings mismatch", content, NotificationType.WARNING);
    }

    @Override
    public String toString() {
      return getContent();
    }
  }
}
