package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public abstract class VcsAction extends AnAction
{
  @NonNls private static final String GROUP_NAME_ID = "OPERATION_FAIL";
  @NonNls private static final String GROUP_TITLE = "Operation Failed";
  @NonNls private static final String OPERATION_FAILED_TEXT = "One or more errors occured during operation";
  @NonNls private static final String TITLE = "Operation Failed on Files:";

  public void update( AnActionEvent e )
  {
    String actionName = getActionName( e );
    if( actionName == null )  throw new IllegalStateException( "Internal error - Action Name is NULL.");
    
    e.getPresentation().setText( actionName );
  }

  protected abstract String getActionName( AnActionEvent e );

  protected void execute( AnActionEvent e, List<VcsException> errors ) {}

  public void actionPerformed( AnActionEvent e )
  {
    Project project = e.getData( DataKeys.PROJECT );
    FileDocumentManager.getInstance().saveAllDocuments();
    List<VcsException> errors = runAction( e );

    if( errors.size() > 0 )
    {
      UpdatedFiles updatedFiles = UpdatedFiles.create();
      updatedFiles.registerGroup( new FileGroup( GROUP_TITLE, GROUP_TITLE, false, GROUP_NAME_ID, true ));

      for( VcsException exc : errors )
      {
        updatedFiles.getGroupById( GROUP_NAME_ID ).add( exc.getVirtualFile().getPath() );
      }

      ProjectLevelVcsManager.getInstance( project ).showProjectOperationInfo( updatedFiles, TITLE );
      Messages.showErrorDialog( project, OPERATION_FAILED_TEXT, GROUP_TITLE );
    }
  }

  protected List<VcsException> runAction( AnActionEvent e )
  {
    List<VcsException> list = new ArrayList<VcsException> ();

    AbstractVcsHelper helper = AbstractVcsHelper.getInstance( e.getData( DataKeys.PROJECT ) );
    LvcsAction lvcsAction = helper.startVcsAction( e.getPresentation().getText() );

    try     {  execute( e, list );  }
    finally {  helper.finishVcsAction( lvcsAction );  }

    return list;
  }

  protected static FileStatus getFileStatus( Project project, VirtualFile file )
  {
    ChangeListManager mgr = ChangeListManager.getInstance( project );
    return mgr.getStatus( file );
  }

  protected static Project getProject( AnActionEvent e )
  {
    return e.getData( DataKeys.PROJECT );
  }

  protected static TransparentVcs getHost( AnActionEvent e )
  {
    return TransparentVcs.getInstance( e.getData( DataKeys.PROJECT ) );
  }
}
