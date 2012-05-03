package net.sourceforge.transparent;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;

/**
 * @author irengrig
 *         Date: 3/22/11
 *         Time: 7:04 PM
 */
@State(
  name = "ClearCaseSharedConfig",
  storages = {@Storage( file = StoragePathMacros.PROJECT_FILE),
    @Storage( file = StoragePathMacros.PROJECT_CONFIG_DIR + "/vcs.xml", scheme = StorageScheme.DIRECTORY_BASED)})
public class CCaseSharedConfig implements PersistentStateComponent<CCaseSharedConfig.State> {
  private State myState;
  private final Project myProject;

  public CCaseSharedConfig(Project project) {
    myProject = project;
    myState = new State();
  }

  public static CCaseSharedConfig getInstance(final Project project) {
    return ServiceManager.getService(project, CCaseSharedConfig.class);
  }

  public static class State {
    public Boolean myUseUcmModel;
  }

  @Override
  public State getState() {
    State state = determineState();
    // default is true
    if (Boolean.TRUE.equals(state.myUseUcmModel)) return null;
    return state;
  }

  private State determineState() {
    if (myState.myUseUcmModel == null) {
      myState.myUseUcmModel = CCaseConfig.getInstance(myProject).useUcmModel;
    }
    return myState;
  }

  @Override
  public void loadState(State  state) {
    myState = state;
  }

  public boolean isUseUcmModel() {
    if (myState.myUseUcmModel == null) {
      myState.myUseUcmModel = CCaseConfig.getInstance(myProject).useUcmModel;
    }
    return Boolean.TRUE.equals(myState.myUseUcmModel);
  }

  public void setUcmMode(final boolean value) {
    myState.myUseUcmModel = value;
  }
}
