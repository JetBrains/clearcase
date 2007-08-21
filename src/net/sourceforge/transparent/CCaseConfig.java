package net.sourceforge.transparent;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * This is the persistent state of the transparent plugin - just anything that needs
 * to be persisted as a field
 */
public class CCaseConfig implements JDOMExternalizable, ProjectComponent
{
  @NonNls private final static String DYNAMIC = "Dynamic";
  @NonNls private final static String SNAPSHOT = "Snapshot";

//  public String implementation = CommandLineClearCase.class.getName();
  public boolean checkoutReserved = false;
  public boolean markExternalChangeAsUpToDate = true;
  public boolean checkInUseHijack = true;
  public boolean useUcmModel = true;
  public boolean isOffline = false;
  public boolean synchOutside = false;
  public boolean isHistoryResticted = true;
  public boolean useIdenticalSwitch = true;
  public String lastScr = "";
  public String scrTextFileName = "";
  public String lastViewType = null;
  public int    historyRevisionsNumber = 4;

  public void projectOpened() {}
  public void projectClosed() {}
  public void initComponent() {}
  public void disposeComponent() {}

  @NotNull
  public String getComponentName() {  return "CCaseConfig";  }

  public void  setViewSnapshot()   {  lastViewType = SNAPSHOT;  }
  public void  setViewDynamic()    {  lastViewType = DYNAMIC;   }
  public boolean isViewSnapshot()  {  return lastViewType == SNAPSHOT;  }
  public boolean isViewDynamic()   {  return lastViewType == DYNAMIC;  }

  public int  getHistoryRevisionsMargin() {  return historyRevisionsNumber;  }
  public void   setHistoryRevisionsMargin( int v) {  historyRevisionsNumber = v;  }

  public static CCaseConfig getInstance(Project project) {
    return project.getComponent(CCaseConfig.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element parentNode) throws WriteExternalException
  {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
  }
}
