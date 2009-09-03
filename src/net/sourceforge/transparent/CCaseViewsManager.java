package net.sourceforge.transparent;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.HashSet;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import net.sourceforge.transparent.exceptions.ClearCaseNoServerException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Oct 18, 2007
 */
public class CCaseViewsManager extends AbstractProjectComponent implements ChangeListDecorator, JDOMExternalizable {
  @NonNls private static final String PERSISTENCY_SAVED_ACTIVITY_MAP_TAG = "ClearCasePersistencyActivitiesMap";

  @NonNls private static final String VIEW_INFO_TAG = "ViewInfo";
  @NonNls private static final String CONTENT_ROOT_TAG = "ContentRoot";
  @NonNls private static final String TAG_TAG = "Tag";
  @NonNls private static final String UUID_TAG = "Uuid";
  @NonNls private static final String UCM_TAG = "Ucm";
  @NonNls private static final String SNAPSHOT_TAG = "Snapshot";
  @NonNls private static final String ACTIVITY_TAG = "Activity";

  @NonNls private static final String TAG_SIG = "Tag: ";
  @NonNls private static final String TAG_UUID_SIG = "  View tag uuid:";
  @NonNls private static final String ATTRIBUTES_SIG = "iew attributes:";
  @NonNls private static final String SNAPSHOT_SIG = "snapshot";
  @NonNls private static final String UCM_SIG = "ucmview";

  @NonNls private static final String LIST_VIEW_CMD = "lsview";
  @NonNls private static final String CURRENT_VIEW_SWITCH = "-cview";
  @NonNls private static final String VIEW_SWITCH = "-view";
  @NonNls private static final String OBSOLETE_SWITCH = "-obsolete";
  @NonNls private static final String FORMAT_SWITCH = "-fmt";
  @NonNls private static final String LONG_SWITCH = "-long";
  @NonNls private static final String LIST_ACTIVITY_CMD = "lsactivity";
  @NonNls private static final String ME_ONLY_SWITCH = "-me";
  @NonNls private static final String LIST_ACTIVITY_FORMAT = "%n <-> %[locked]p <-> %[headline]p <-> %[view]p\\n";
  @NonNls private static final String FIELDS_DELIMITER = " <-> ";

  @NonNls private static final String LOCKED_ACTIVITY_SIG = " (LOCKED)";
  @NonNls private static final String NOT_ASSOCIATED_CHANGELIST_SIG = " (not associated with any CC activity)";

  @NonNls private static final String ERRORS_TAB_NAME = "ClearCase views operations";
  @NonNls private static final String SERVER_UNAVAILABLE_MESSAGE =
    "\nServer is unavailable, ClearCase support is switched to isOffline mode";
  @NonNls private static final String FAILED_TO_INIT_VIEW_MESSAGE = "Plugin failed to initialize view:\n";
  @NonNls private static final String FAILED_TO_COLLECT_VIEW_MESSAGE =
    "Plugin failed to collect information on views (absent 'cleartool.exe'?). Plugin is switched to the offline mode.\n";

  public HashMap<String, ViewInfo> viewsMapByRoot;

  //  Keeps for any checked out file the activity which it was checked out with
  private final HashMap<String, String> activitiesAssociations;
  private final HashMap<String, ActivityInfo> activitiesMap;

  public static class ViewInfo {
    public String tag;
    public String uuid;
    public boolean isSnapshot;
    public boolean isUcm;
    public ActivityInfo currentActivity;
  }

  public static class ActivityInfo {
    public ActivityInfo(@NotNull String actName, @NotNull String pubName, @NonNls @NotNull String isObs, String inView) {
      name = actName;
      publicName = pubName;
      isObsolete = isObs.equals("obsolete");
      isLocked = isObs.equals("locked");
      activeInView = inView == null ? null : inView.trim();
    }

    public String name;
    public String publicName;
    public boolean isObsolete;
    public boolean isLocked;
    public String activeInView;
  }

  @NotNull
  public String getComponentName() {
    return "CCaseViewsManager";
  }

  public static CCaseViewsManager getInstance(Project project) {
    return project.getComponent(CCaseViewsManager.class);
  }

  public CCaseViewsManager(Project project) {
    super(project);

    viewsMapByRoot = new HashMap<String, ViewInfo>();

    activitiesAssociations = new HashMap<String, String>();
    activitiesMap = new HashMap<String, ActivityInfo>();
  }

  public boolean isAnyUcmView() {
    boolean status = false;
    for (ViewInfo info : viewsMapByRoot.values()) {
      status = status || info.isUcm;
    }

    return status;
  }

  public boolean isAnySnapshotView() {
    boolean status = false;
    for (ViewInfo info : viewsMapByRoot.values()) {
      status = status || info.isSnapshot;
    }

    return status;
  }

  @Nullable
  public ViewInfo getViewByRoot(VirtualFile root) {
    return root != null ? viewsMapByRoot.get(root.getPath()) : null;
  }

  @Nullable
  public ViewInfo getViewByFile(VirtualFile file) {
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(myProject);
    VirtualFile root = mgr.getVcsRootFor(file);
    return getViewByRoot(root);
  }

  @Nullable
  public ViewInfo getViewByFile(FilePath file) {
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(myProject);
    VirtualFile root = mgr.getVcsRootFor(file);
    return getViewByRoot(root);
  }

  @Nullable
  public ActivityInfo getActivityForName(String name) {
    for (ActivityInfo info : activitiesMap.values()) {
      if (info.publicName.equalsIgnoreCase(name)) return info;
    }
    return null;
  }

  public boolean isUcmViewForFile(VirtualFile file) {
    ViewInfo view = getViewByFile(file);
    return view != null && view.isUcm;
  }

  public boolean isUcmViewForFile(FilePath file) {
    ViewInfo view = getViewByFile(file);
    return view != null && view.isUcm;
  }

  /**
   * Retrieve basic view's properties - Tag, type, activity, uuid.
   * For each content root (the CCase view), issue the command
   * "cleartool lsview -cview" from the working folder equals to that root.
   */
  public void reloadViews() {
    TransparentVcs host = TransparentVcs.getInstance(myProject);
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance(myProject);
    CCaseConfig config = CCaseConfig.getInstance(myProject);

    VirtualFile[] roots = mgr.getRootsUnderVcs(host);
    try {
      loadAbsentViews(roots);
      removeObsoleteViews(roots);
      logViewsByName(viewsMapByRoot);

      if (config.useUcmModel) {
        extractViewActivities();
        checkViewsWithoutActions();
        synchActivities2ChangeLists(null);
      }
    }
    catch (ClearCaseNoServerException e) {
      String message = SERVER_UNAVAILABLE_MESSAGE + e.getMessage();
      AbstractVcsHelper.getInstance(myProject).showError(new VcsException(message), ERRORS_TAB_NAME);

      config.isOffline = true;
    }
    catch (ClearCaseException e) {
      //  It is possible that some configuration paths point to an invalid
      //  or obsolete view.
      String message = FAILED_TO_INIT_VIEW_MESSAGE + e.getMessage();
      AbstractVcsHelper.getInstance(myProject).showError(new VcsException(message), ERRORS_TAB_NAME);
    }
    catch (RuntimeException e) {
      String message = FAILED_TO_COLLECT_VIEW_MESSAGE + e.getMessage();
      AbstractVcsHelper.getInstance(myProject).showError(new VcsException(message), ERRORS_TAB_NAME);

      config.isOffline = true;
    }
  }

  private void loadAbsentViews(VirtualFile[] roots) {
    for (VirtualFile root : roots) {
      if (!viewsMapByRoot.containsKey(root.getPath())) {
        ViewInfo info = new ViewInfo();

        extractViewType(root.getPath(), info);
        viewsMapByRoot.put(root.getPath(), info);
      }
    }
  }

  /**
   * Remove those ViewInfo_s which do not correspond to any content roots
   * currently configured in the application.
   */
  private void removeObsoleteViews(VirtualFile[] roots) {
    Set<String> storedRoots = new HashSet<String>(viewsMapByRoot.keySet());
    for (String storedRoot : storedRoots) {
      boolean isFound = false;
      for (VirtualFile root : roots) {
        if (storedRoot.equals(root.getPath())) {
          isFound = true;
          break;
        }
      }
      if (!isFound) {
        viewsMapByRoot.remove(storedRoot);
      }
    }
  }

  private static void extractViewType(String viewPath, ViewInfo info) throws ClearCaseNoServerException {
    String output = TransparentVcs.cleartoolOnLocalPathWithOutput(viewPath, LIST_VIEW_CMD, CURRENT_VIEW_SWITCH, LONG_SWITCH);
    if (TransparentVcs.isServerDownMessage(output)) throw new ClearCaseNoServerException(output);

    List<String> lines = StringUtil.split(output, "\n");
    for (String line : lines) {
      if (line.startsWith(TAG_SIG)) {
        info.tag = line.substring(TAG_SIG.length());

        // IDEADEV-23797 - when present, view's comment is printed along with
        // the view's tag. Strip it (it starts with the quote char).
        //  NB: assuming that view's tag can not contain quote symbols.
        int quoteIndex = info.tag.indexOf('"');
        if (quoteIndex != -1) info.tag = info.tag.substring(0, quoteIndex - 1).trim();
      }
      else if (line.startsWith(TAG_UUID_SIG)) {
        info.uuid = line.substring(TAG_UUID_SIG.length());
      }
      else if (line.indexOf(ATTRIBUTES_SIG) != -1) {
        //  When analyzing the type of the view (dynamic or snapshot):
        //  value "snapshot" appears in "View attributes:..." line only in the
        //  case of snapshot view (lol). If the value is not present, assume the
        //  view is dynamic.
        info.isSnapshot = line.indexOf(SNAPSHOT_SIG) != -1;
        info.isUcm = line.indexOf(UCM_SIG) != -1;
        break;
      }
    }
  }

  // log result views list
  private static void logViewsByName(HashMap<String, ViewInfo> views) {
    TransparentVcs.LOG.info(">>> Views list:");
    for (String root : views.keySet()) {
      TransparentVcs.LOG.info("\t\t" + root + " -> " + views.get(root).tag);
    }
  }

  private void checkViewsWithoutActions() {
    Set<String> passiveViews = new HashSet<String>();
    for (ViewInfo info : viewsMapByRoot.values()) {
      if (info.currentActivity == null) {
        passiveViews.add(info.tag);
      }
    }
    if (passiveViews.size() > 0) {
      List<VcsException> list = new ArrayList<VcsException>();
      for (String view : passiveViews) {
        VcsException warn = new VcsException("View " + view + " has no associated activity. Checkout from this view will be problematic.");
        warn.setIsWarning(true);
        list.add(warn);
      }
      AbstractVcsHelper.getInstance(myProject).showErrors(list, ERRORS_TAB_NAME);
      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
        }
      });
    }
  }

  /**
   * Iterate over views, issue "lsactivity" for the view, collect all activities
   * associated with the view. Remember its status - normal, locked or obsolete.
   */
  public void extractViewActivities() {
    if (CCaseConfig.getInstance(myProject).isOffline) return;

    //  Sometimes users configure content roots so that several of them correspond
    //  to the same view. Thus we should not repeat the command for the same view
    //  more than one time.
    HashSet<String> completedViews = new HashSet<String>();

    activitiesMap.clear();
    for (ViewInfo info : viewsMapByRoot.values()) {
      if (info.isUcm && !completedViews.contains(info.tag)) {
        String output = TransparentVcs
          .cleartoolWithOutput(LIST_ACTIVITY_CMD, ME_ONLY_SWITCH, OBSOLETE_SWITCH, VIEW_SWITCH, info.tag, FORMAT_SWITCH,
                               LIST_ACTIVITY_FORMAT);
        if (TransparentVcs.isServerDownMessage(output)) return;

        completedViews.add(info.tag);
        if (!StringUtil.isEmptyOrSpaces(output)) TransparentVcs.LOG.info(output);

        //  Reset values so that we can always determine that we did not manage
        //  to correctly parse "lsactivity" command's output.
        info.currentActivity = null;

        String[] lines = LineTokenizer.tokenize(output, false);
        for (String line : lines) {
          ActivityInfo actInfo = parseActivities(line);
          if (actInfo != null) //  successful parse?
          {
            activitiesMap.put(actInfo.name, actInfo);
            associateActivityWithView(actInfo);
          }
        }
      }
    }

    TransparentVcs.LOG.info(">>> Default Activities Detected:");
    for (String actName : activitiesMap.keySet()) {
      ActivityInfo actInfo = activitiesMap.get(actName);
      if (actInfo.activeInView != null) TransparentVcs.LOG.info(">>>\t\t[" + actName + "] -> [" + actInfo.activeInView + "]");
    }

    TransparentVcs.LOG.info("\n>>> Extracted Activities:");
    for (ViewInfo info : viewsMapByRoot.values()) {
      if (info.isUcm) {
        if (info.currentActivity != null) {
          TransparentVcs.LOG.info(">>>\t" + info.tag + " -> " + info.currentActivity.publicName);
        }
        else {
          TransparentVcs.LOG.info(">>>\t" + info.tag + " has no default activity");
        }
      }
    }
  }

  public void synchActivities2ChangeLists(final ChangeListManagerGate gate) {
    LocalChangeList nonDefltList = null;
    LocalChangeList defltListToDelete = null;
    ChangeListManager mgr = ChangeListManager.getInstance(myProject);

    for (ActivityInfo info : activitiesMap.values()) {
      LocalChangeList list = gate == null ? mgr.findChangeList(info.publicName) : gate.findChangeList(info.publicName);
      if (list != null) {
        if (info.isObsolete) {
          if (!list.isDefault()) {
            mgr.removeChangeList(list.getName());
          }
          else {
            defltListToDelete = list;
          }
        }
        else {
          nonDefltList = list;
        }
      }
      else if (!info.isObsolete) {
        nonDefltList = gate == null ? mgr.addChangeList(info.publicName, null) : gate.addChangeList(info.publicName, null);
      }
    }

    if (defltListToDelete != null && nonDefltList != null) {
      mgr.setDefaultChangeList(nonDefltList);
      mgr.removeChangeList(defltListToDelete.getName());
    }
  }

  /**
   * Given the checkout activity for a file, compare it with the current activity
   * for a file's view. If they differ (it means that the user has changed current
   * activity for a view in CCaseExp in the background and we did not synchronized
   * IDEA) - change the current activity for a view with that from file's checkout.
   */
  public void checkChangedActivityForView(String fileName, String activityName) {
    VirtualFile file = VcsUtil.getVirtualFile(fileName);
    if (file != null) {
      ViewInfo view = getViewByFile(file);
      if (view != null && needToChangeActivity(view, activityName)) {
        ActivityInfo activity = getActivityForName(activityName);
        if (activity != null) {
          if (view.currentActivity != null) view.currentActivity.activeInView = null;

          view.currentActivity = activity;
          activity.activeInView = view.tag;
        }
      }
    }
  }

  private static boolean needToChangeActivity(ViewInfo view, String activityName) {
    return view.currentActivity == null || !view.currentActivity.publicName.equalsIgnoreCase(activityName);
  }

  @Nullable
  private static ActivityInfo parseActivities(String str) {
    ActivityInfo info = null;
    String[] tokens = str.split(FIELDS_DELIMITER);
    if (tokens.length == 4) //  successful extraction
    {
      info = new ActivityInfo(tokens[0], tokens[2], tokens[1], tokens[3]);
    }
    else if (tokens.length == 3) //  activity is not current for any view.
    {
      info = new ActivityInfo(tokens[0], tokens[2], tokens[1], null);
    }

    return info;
  }

  private void associateActivityWithView(@NotNull ActivityInfo activityInfo) {
    if (activityInfo.activeInView != null) {
      for (ViewInfo view : viewsMapByRoot.values()) {
        if (view.tag.equals(activityInfo.activeInView)) view.currentActivity = activityInfo;
      }
    }
  }

  public void addFile2Changelist(String fileName, @NotNull String changeListName) {
    String normName = VcsUtil.getCanonicalLocalPath(fileName);
    activitiesAssociations.put(normName, changeListName);
  }

  public void removeFileFromActivity(String fileName) {
    fileName = VcsUtil.getCanonicalLocalPath(fileName);
    activitiesAssociations.remove(fileName);
  }

  @Nullable
  public String getCheckoutActivityForFile(@NotNull String fileName) {
    return activitiesAssociations.get(fileName);
  }

  @Nullable
  public String getActivityDisplayName(String activity) {
    ActivityInfo info = activitiesMap.get(activity);
    return info != null ? info.publicName : null;
  }


  public String getActivityIdName(String activity) {
    for (ActivityInfo info : activitiesMap.values()) {
      if (info.publicName.equals(activity)) return info.name;
    }

    //  Strip character which are not allowed in the activity normalized name
    //  (the list is not complete I suppose).
    return activity.replaceAll("[ ()=]", "_");
  }

  @Nullable
  public String getActivityOfViewOfFile(final FilePath path) {
    VirtualFile root = VcsUtil.getVcsRootFor(myProject, path);
    return root != null ? getActivityOfViewByRoot(root) : null;
  }

  @Nullable
  public String getActivityOfViewOfFile(final VirtualFile file) {
    VirtualFile root = VcsUtil.getVcsRootFor(myProject, file);
    return root != null ? getActivityOfViewByRoot(root) : null;
  }

  @Nullable
  public String getActivityOfViewByRoot(@NotNull final VirtualFile root) {
    String activity = null;
    ViewInfo info = viewsMapByRoot.get(root.getPath());
    if (info != null && info.isUcm && info.currentActivity != null) {
      activity = info.currentActivity.publicName;
    }
    return activity;
  }

  /**
   * Collect all activities which are "current" or "active" in their views.
   */
  public List<String> getDefaultActivities() {
    List<String> activities = new ArrayList<String>();
    for (ActivityInfo info : activitiesMap.values()) {
      if (info.activeInView != null) activities.add(info.publicName);
    }

    return activities;
  }

  public void decorateChangeList(final LocalChangeList changeList,
                                 @NonNls final ColoredTreeCellRenderer cellRenderer,
                                 boolean selected,
                                 boolean expanded,
                                 boolean hasFocus) {
    ChangesUtil.processChangesByVcs(myProject, changeList.getChanges(), new ChangesUtil.PerVcsProcessor<Change>() {
      public void process(final AbstractVcs vcs, final List<Change> items) {
        if (vcs == TransparentVcs.getInstance(myProject)) {
          decorateClearCaseChangelist(changeList, cellRenderer);
        }
      }
    });
  }

  private void decorateClearCaseChangelist(final LocalChangeList changeList, final ColoredTreeCellRenderer cellRenderer) {
    ActivityInfo info = getActivityForName(changeList.getName());
    if (info != null) {
      if (info.isLocked) cellRenderer.append(LOCKED_ACTIVITY_SIG, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
    else {
      cellRenderer.append(NOT_ASSOCIATED_CHANGELIST_SIG, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  //
  // JDOMExternalizable methods
  //
  public void readExternal(final Element element) throws InvalidDataException {
    TransparentVcs.readRenamedElements(element, activitiesAssociations, PERSISTENCY_SAVED_ACTIVITY_MAP_TAG, false);

    List elements = element.getChildren(VIEW_INFO_TAG);
    for (Object cclObj : elements) {
      if (cclObj instanceof Element) {
        ViewInfo info = new ViewInfo();
        info.tag = ((Element)cclObj).getChild(TAG_TAG).getValue();

        //  IDEADEV-19094. Can it be so that View may NOT have an UUID tag?
        //  Or we were fucked by the parsing output?
        if (((Element)cclObj).getChild(UUID_TAG) != null) info.uuid = ((Element)cclObj).getChild(UUID_TAG).getValue();

        info.isUcm = Boolean.valueOf(((Element)cclObj).getChild(UCM_TAG).getValue()).booleanValue();
        info.isSnapshot = Boolean.valueOf(((Element)cclObj).getChild(SNAPSHOT_TAG).getValue()).booleanValue();

        String root = ((Element)cclObj).getChild(CONTENT_ROOT_TAG).getValue();
        viewsMapByRoot.put(root, info);
      }
    }
    extractViewActivities();
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    for (String root : viewsMapByRoot.keySet()) {
      final ViewInfo info = viewsMapByRoot.get(root);
      final Element listElement = new Element(VIEW_INFO_TAG);

      listElement.addContent(new Element(CONTENT_ROOT_TAG).addContent(root));
      listElement.addContent(new Element(TAG_TAG).addContent(info.tag));
      listElement.addContent(new Element(UUID_TAG).addContent(info.uuid));
      listElement.addContent(new Element(UCM_TAG).addContent(Boolean.toString(info.isUcm)));
      listElement.addContent(new Element(SNAPSHOT_TAG).addContent(Boolean.toString(info.isSnapshot)));

      if (info.currentActivity != null) listElement.addContent(new Element(ACTIVITY_TAG).addContent(info.currentActivity.name));

      element.addContent(listElement);
    }

    TransparentVcs.writePairedElement(element, activitiesAssociations, PERSISTENCY_SAVED_ACTIVITY_MAP_TAG);
  }
}
