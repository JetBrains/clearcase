package net.sourceforge.transparent;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.intellij.plugins.ListenerNotifier;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * this is the persistent state of the transparent plugin - just anything that needs to be persisted as a field
 */
public class CCaseConfig implements ListenerNotifier, JDOMExternalizable, ProjectComponent
{
  @NonNls private final static String DYNAMIC = "Dynamic";
  @NonNls private final static String SNAPSHOT = "Snapshot";

  public String implementation = CommandLineClearCase.class.getName();
  public String clearcaseRoot = "";
  public boolean checkoutReserved = false;
  public boolean markExternalChangeAsUpToDate = true;
  public boolean checkInUseHijack = true;
  public boolean offline = false;
  public String lastScr = "";
  public String scrTextFileName = "";
  public String lastViewType = null;

  private PropertyChangeSupport listenerSupport = new PropertyChangeSupport(this);

  private static final Logger LOG = Logger.getInstance("net.sourceforge.transparent.CCaseConfig");

  public void projectOpened() {  LOG.debug("projectOpened");  }
  public void projectClosed() {}
  public void initComponent() {  logConfig();   }
  public void disposeComponent() {}

  @NotNull
  public String getComponentName() {  return "CCaseConfig";  }

  public void  setViewSnapshot()   {  lastViewType = SNAPSHOT;  }
  public void  setViewDynamic()    {  lastViewType = DYNAMIC;   }
  public boolean isViewSnapshot()  {  return lastViewType == SNAPSHOT;  }
  public boolean isViewDynamic()   {  return lastViewType == DYNAMIC;  }

  private void logConfig()
  {
    LOG.debug( "#####    implementation        = " + implementation );
    LOG.debug( "#####    clearcaseRoot         = " + clearcaseRoot );
    LOG.debug( "#####    checkoutReserved      = " + checkoutReserved );
    LOG.debug( "#####    externalChangeUpToDate= " + markExternalChangeAsUpToDate );
    LOG.debug( "#####    checkInUseHijack      = " + checkInUseHijack );
    LOG.debug( "#####    offline               = " + offline );
    LOG.debug( "#####    lastScr               = " + lastScr );
    LOG.debug( "#####    scrTextFileName       = " + scrTextFileName );
    LOG.debug( "#####    last view type        = " + lastViewType );
  }

  public static CCaseConfig getInstance(Project project) {
    return project.getComponent(CCaseConfig.class);
  }

  public PropertyChangeListener[] getListeners() {
    return listenerSupport.getPropertyChangeListeners();
  }

  public void addListener(PropertyChangeListener listener) {
    listenerSupport.addPropertyChangeListener(listener);
  }

  public void notifyListenersOfChange() {
    logConfig();
    listenerSupport.firePropertyChange("configuration", null, this);
  }

  public void removeListener(PropertyChangeListener listener) {
    listenerSupport.removePropertyChangeListener(listener);
  }

  public static String[] getAvailableImplementations() {
     return new String[]{ CommandLineClearCase.class.getName(),
                          "net.sourceforge.transparent.NativeClearCase",
                          "net.sourceforge.transparent.NewNativeClearCase"
                        };
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element parentNode) throws WriteExternalException
  {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
  }
}
