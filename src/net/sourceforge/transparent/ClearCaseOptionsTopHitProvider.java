package net.sourceforge.transparent;

import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.PublicFieldBasedOptionDescription;
import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
public final class ClearCaseOptionsTopHitProvider extends OptionsTopHitProvider {
  private static final String ID = "vcs.ClearCase";

  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(@Nullable final Project project) {
    if (project != null) {
      for (VcsDescriptor descriptor : ProjectLevelVcsManager.getInstance(project).getAllVcss()) {
        if ("ClearCase".equals(descriptor.getDisplayName())) {
          ArrayList<BooleanOptionDescription> options = new ArrayList<BooleanOptionDescription>();
          CCaseViewsManager manager = CCaseViewsManager.getInstance(project);
          final CCaseConfig config = CCaseConfig.getInstance(project);
          if (manager.isAnySnapshotView()) {
            options.add(
              new PublicMethodBasedOptionDescription("ClearCase: Work Offline (on edit: hijack instead of checkout)", ID, "isOffline", "setOfflineMode") {
                @Override
                public Object getInstance() {
                  return config;
                }
              });
          }
          options.add(option(config, "ClearCase: Reserved Checkouts", "checkoutReserved"));
          options.add(option(config, "ClearCase: Check out automatically hijacked file on check in", "checkInUseHijack"));
          options.add(new PublicMethodBasedOptionDescription("ClearCase: Use UCM model", ID, "isUseUcmModel", "setUcmMode") {
            @Override
            public Object getInstance() {
              return CCaseSharedConfig.getInstance(project);
            }

            @Override
            public void setOptionState(boolean enabled) {
              super.setOptionState(enabled);
              if (enabled) {
                CCaseViewsManager.getInstance(project).extractViewActivities();
              }
            }

            @Override
            protected void fireUpdated() {
              TransparentVcs.getInstance(project).checkRootsForUCMMismatch();
            }
          });
          options.add(option(config, "ClearCase: Synchronize activities on refresh", "synchActivitiesOnRefresh"));
          options.add(option(config, "ClearCase: Use \"-identical\" switch during check in", "useIdenticalSwitch"));
          options.add(option(config, "ClearCase: Restrict history records by " + config.getHistoryRevisionsMargin(), "isHistoryResticted"));
          return Collections.unmodifiableCollection(options);
        }
      }
    }
    return Collections.emptyList();
  }

  private static BooleanOptionDescription option(final Object instance, String option, String field) {
    return new PublicFieldBasedOptionDescription(option, ID, field) {
      @Override
      public Object getInstance() {
        return instance;
      }
    };
  }
}
