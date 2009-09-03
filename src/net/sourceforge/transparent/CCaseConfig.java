package net.sourceforge.transparent;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * This is the persistent state of the transparent plugin - just anything that needs
 * to be persisted as a field
 */
public class CCaseConfig extends AbstractProjectComponent implements JDOMExternalizable {
  public boolean checkoutReserved = false;
  public boolean markExternalChangeAsUpToDate = true;
  public boolean checkInUseHijack = true;
  public boolean useUcmModel = true;
  public boolean isOffline = false;
  public boolean synchOutside = false;
  public boolean isHistoryResticted = true;
  public boolean useIdenticalSwitch = true;
  public boolean synchActivitiesOnRefresh = true;
  public String lastScr = "";
  public String scrTextFileName = "";
  public int historyRevisionsNumber = 4;

  private TransparentVcs host;

  public CCaseConfig(Project project) {
    super(project);
  }

  @NotNull
  public String getComponentName() {
    return "CCaseConfig";
  }

  public static CCaseConfig getInstance(Project project) {
    return project.getComponent(CCaseConfig.class);
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

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
  }
}
