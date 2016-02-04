package net.sourceforge.transparent;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;

@State(name = "ClearCaseSharedConfig", storages = @Storage("vcs.xml"))
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
