package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import org.intellij.plugins.ListenerNotifier;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * this is the persistent state of the transparent plugin - just anything that needs to be persisted as a field
 */
public class TransparentConfiguration implements ListenerNotifier, JDOMExternalizable, ProjectComponent
{
  @NonNls private static final String MARK_EXTERNAL_CHANGES_AS_UP_TO_DATE_FIELD = "MARK_EXTERNAL_CHANGES_AS_UP_TO_DATE";

  public String implementation = CommandLineClearCase.class.getName();
  public String clearcaseRoot = "";
  public boolean checkoutReserved = false;
  public boolean markExternalChangeAsUpToDate = true;
  public boolean checkInUseHijack = true;
  public boolean offline = false;
  public String maskExcludedFilesPatterns = "";

  private Project project;
  private PropertyChangeSupport listenerSupport = new PropertyChangeSupport(this);
  /*
  private Field markExternalChangesAsUpToDateField;
  private BaseComponent lvcsConfiguration;
  */
  private HashSet<Pattern> extsByType = new HashSet<Pattern>();

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element parentNode) throws WriteExternalException
  {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    compilePattern( maskExcludedFilesPatterns );
  }

   public TransparentConfiguration(Project project) {  this.project = project;  }

   public void projectOpened() {  LOG.debug("projectOpened");  }
   public void projectClosed() {}

   public void initComponent() {
       logConfig();
//       initExternalChangesAreUpToDateField();
   }
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
    }

   /*
    private void initExternalChangesAreUpToDateField() {
      if (project == null || !isAriadna()) {return;}
      lvcsConfiguration = getLvcsConfiguration(project);
      if (lvcsConfiguration != null) {
         markExternalChangesAsUpToDateField = getMarkExternalChangesAsUpToDateField(lvcsConfiguration);
         resetLcvsConfiguration();
      } else {
         LOG.debug("Found no LvcsConfiguration. MarkExternalChangesAsUpToDate won't work");
      }
   }
   */

   private static boolean isAriadna() {
      ApplicationInfo info = ApplicationInfo.getInstance();
      LOG.debug( "##### IDEA Used = " + info.getVersionName() + " " +
                 info.getMajorVersion() + "." + info.getMinorVersion() + " " + info.getBuildNumber());

      return info.getVersionName().equals("Ariadna");
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
//      resetLcvsConfiguration();
   }

   public void removeListener(PropertyChangeListener listener) {
      listenerSupport.removePropertyChangeListener(listener);
   }

  @Nullable
  public static BaseComponent getLvcsConfiguration(Project project)
  {
    Object[] components = project.getComponents(Object.class);
    for (Object c : components) {
      if (BaseComponent.class.isAssignableFrom(c.getClass())) {
        BaseComponent bc = (BaseComponent)c;
        if (bc.getComponentName().equals("LvcsConfiguration") || bc.getComponentName().equals("LvcsProjectConfiguration")) {
          return bc;
        }
      }
    }
    LOG.debug("Could not find LvcsConfiguration");
    return null;
   }

   public static Field getMarkExternalChangesAsUpToDateField(BaseComponent lvcsConfiguration) {
      try {
         return lvcsConfiguration.getClass().getField(MARK_EXTERNAL_CHANGES_AS_UP_TO_DATE_FIELD);
      } catch (NoSuchFieldException e) {
         LOG.debug("Could not find field " + MARK_EXTERNAL_CHANGES_AS_UP_TO_DATE_FIELD);
      } catch (SecurityException e) {
         LOG.debug("Could not access field " + MARK_EXTERNAL_CHANGES_AS_UP_TO_DATE_FIELD);
      }
      return null;
   }

   /*
   private void resetLcvsConfiguration() {
      if (markExternalChangesAsUpToDateField != null) {
         try {
            markExternalChangesAsUpToDateField.setBoolean(lvcsConfiguration, markExternalChangeAsUpToDate);
         } catch (SecurityException e) {
            LOG.debug(e);
         } catch (IllegalAccessException e) {
            LOG.debug(e);
         }
      }
   }
   */

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
  public String getExcludedMask() {  return maskExcludedFilesPatterns;  }

  public void setExcludedMask( final String masks )
  {
    maskExcludedFilesPatterns = masks;
    extsByType.clear();
    String[] byType = masks.split( ";" );
    for( String mask : byType )
    {
      Pattern p = compilePattern( mask );
      if( p != null )
      {
        extsByType.add( p );
      }
    }
  }

  public boolean isFileExcluded( final String fileName )
  {
    for( Pattern p : extsByType )
    {
      if( p.matcher( fileName ).matches() )
        return true;
    }
    return false;
  }

  @Nullable
  private static Pattern compilePattern( String template )
  {
    Pattern p = null;
    String strPattern = convertWildcard2Pattern( template.trim() );
    try
    {
      if( SystemInfo.isFileSystemCaseSensitive )
        p = Pattern.compile( strPattern );
      else
        p = Pattern.compile( strPattern, Pattern.CASE_INSENSITIVE );
    }
    catch( PatternSyntaxException e )
    {
      //  Generally - nothing to do.
      //noinspection HardCodedStringLiteral
      System.out.println( "Can not parse template: " + template );
    }
    return p;
  }

  private static String convertWildcard2Pattern( String wildcardPattern )
  {
    return wildcardPattern.
      replaceAll("\\\\!", "!").
      replaceAll("\\.", "\\\\.").
      replaceAll("\\*\\?", ".+").
      replaceAll("\\?\\*", ".+").
      replaceAll("\\*", ".*").
      replaceAll("\\?", ".").
      replaceAll("(?:\\.\\*)+", ".*");  // optimization
  }
}
