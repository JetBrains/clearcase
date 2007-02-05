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
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * this is the persistent state of the transparent plugin - just anything that needs to be persisted as a field
 */
public class TransparentConfiguration implements ListenerNotifier, JDOMExternalizable, ProjectComponent
{
  public String implementation = CommandLineClearCase.class.getName();
  public String clearcaseRoot = "";
  public boolean checkoutReserved = false;
  public boolean markExternalChangeAsUpToDate = true;
  public boolean checkInUseHijack = true;
  public boolean offline = false;
  public String lastScr = "";
  public String scrTextFileName = "";

  private PropertyChangeSupport listenerSupport = new PropertyChangeSupport(this);

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element parentNode) throws WriteExternalException
  {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
  }

  public void projectOpened() {  LOG.debug("projectOpened");  }
  public void projectClosed() {}
  public void initComponent() {  logConfig();   }
  public void disposeComponent() {}

  @NotNull
  public String getComponentName() {  return "TransparentConfiguration";  }

  private void logConfig() {
    LOG.debug("##### Loading " + TransparentVcs.class.getName() + " version " + new Version().getVersion() + "###########");
    LOG.debug("#####    implementation        = " + implementation);
    LOG.debug("#####    clearcaseRoot         = " + clearcaseRoot);
    LOG.debug("#####    checkoutReserved      = " + checkoutReserved);
    LOG.debug("#####    externalChangeUpToDate= " + markExternalChangeAsUpToDate);
    LOG.debug("#####    checkInUseHijack      = " + checkInUseHijack);
    LOG.debug("#####    offline               = " + offline);
    LOG.debug("#####    lastScr               = " + offline);
    LOG.debug("#####    scrTextFileName       = " + offline);
  }

  public static TransparentConfiguration getInstance(Project project) {
    return project.getComponent(TransparentConfiguration.class);
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

  private static final Logger LOG = Logger.getInstance("net.sourceforge.transparent.TransparentConfiguration");

  public static String[] getAvailableImplementations() {
     return new String[]{
//         NewCommandLineClearCase.class.getName(),
        CommandLineClearCase.class.getName(),
        "net.sourceforge.transparent.NativeClearCase",
        "net.sourceforge.transparent.NewNativeClearCase",
        TestClearCase.class.getName()
     };
   }
}
