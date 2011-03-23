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
  storages = {@Storage(id = "ClearCaseSharedConfig", file = "$PROJECT_FILE$"),
    @Storage(id = "ClearCaseSharedConfig", file = "$PROJECT_CONFIG_DIR$/vcs.xml", scheme = StorageScheme.DIRECTORY_BASED)})
public class CCaseSharedConfig extends AbstractProjectComponent implements PersistentStateComponent<CCaseSharedConfig.State> {
  private State myState;

  public CCaseSharedConfig(Project project) {
    super(project);
    myState = new State();
  }

  public static CCaseSharedConfig getInstance(final Project project) {
    return project.getComponent(CCaseSharedConfig.class);
  }

  public static class State {
    public Boolean myUseUcmModel;
  }

  @Override
  public State getState() {
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
