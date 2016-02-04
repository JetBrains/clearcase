package net.sourceforge.transparent;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * This is the persistent state of the transparent plugin - just anything that needs
 * to be persisted as a field
 */
@State(
  name = "CCaseConfig",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)
public class CCaseConfig implements PersistentStateComponent<CCaseConfig> {
  public boolean checkoutReserved = false;
  public boolean markExternalChangeAsUpToDate = true;
  public boolean checkInUseHijack = true;
  /** @deprecated use {@link net.sourceforge.transparent.CCaseSharedConfig#isUseUcmModel()} instead
   */
  public boolean useUcmModel = true;
  private boolean isOffline = false;
  public boolean synchOutside = false;
  public boolean isHistoryResticted = true;
  public boolean useIdenticalSwitch = true;
  public boolean synchActivitiesOnRefresh = true;
  public String lastScr = "";
  public String scrTextFileName = "";
  public int historyRevisionsNumber = 4;

  private TransparentVcs host;

  @Override
  public CCaseConfig getState() {
    return this;
  }

  @Override
  public void loadState(CCaseConfig state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static CCaseConfig getInstance(Project project) {
    return ServiceManager.getService(project, CCaseConfig.class);
  }

  public void setHost(TransparentVcs host) {
    this.host = host;
  }

  public int getHistoryRevisionsMargin() {
    return historyRevisionsNumber;
  }

  public void setHistoryRevisionsMargin(int v) {
    historyRevisionsNumber = v;
  }

  public void setOfflineMode(boolean isOfflineMode) {
    if (isOffline != isOfflineMode) {
      isOffline = isOfflineMode;
      if (host != null) {
        host.offlineModeChanged();
      }
    }
  }

  public boolean isOffline() {
    return isOffline;
  }
}
