package net.sourceforge.transparent.actions;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.CCaseConfig;
import org.jetbrains.annotations.NonNls;

public class UpdateFileAction extends AsynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Update File";

  public static final String HIJACKED_MESSAGE = " is hijacked.\n" + "Do you want to continue and lose your local changes?" ;
  public static final String CHECKED_OUT_MESSAGE = " is checked out.\n" + "Undo checkout before updating it";

  //  "Update File" command (and all other "Update smth..." commands as well)
  //  are valid only for a snapshot views, they just produce no effect on
  //  dynamic views. But from the usability point of view it is better not to
  //  allow this command at all.
  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    CCaseConfig config = CCaseConfig.getInstance( e.getData( DataKeys.PROJECT ) );
    return super.isEnabled( file, e ) && !config.isViewDynamic();
  }

  public void perform( VirtualFile vfile, AnActionEvent e )
  {
    cleartool( "update", "-graphical", vfile.getPath() );
  }

  protected String getActionName( AnActionEvent e ) {  return ACTION_NAME;   }
}
