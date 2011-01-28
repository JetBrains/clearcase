package net.sourceforge.transparent;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import net.sourceforge.transparent.Checkin.CCaseCheckinHandler;
import org.jetbrains.annotations.NotNull;

/**
* @author irengrig
*         Date: 1/28/11
*         Time: 6:08 PM
*/
public class TransparentVcsCheckinHandlerFactory extends VcsCheckinHandlerFactory {
  public TransparentVcsCheckinHandlerFactory() {
    super(TransparentVcs.getKey());
  }

  @NotNull
  @Override
  protected CheckinHandler createVcsHandler(CheckinProjectPanel panel) {
    return new CCaseCheckinHandler( TransparentVcs.getInstance(panel.getProject()), panel );
  }
}
