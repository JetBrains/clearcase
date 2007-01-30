package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.TransparentVcs;
import org.intellij.plugins.util.LogUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class VcsAction extends AnAction
{
  public static final Logger LOG = LogUtil.getLogger();

  public void update( AnActionEvent e )
  {
    String actionName = getActionName();
    if( actionName == null )  throw new IllegalStateException( "Internal error - Action Name is NULL.");
    
    e.getPresentation().setText( actionName );
  }

  protected abstract String getActionName();

  protected void execute( AnActionEvent e, List<VcsException> errors ) {}

  public void actionPerformed( AnActionEvent e )
  {
    FileDocumentManager.getInstance().saveAllDocuments();
    List<VcsException> list = runAction( e );
    if( list.size() > 0 )
    {
      Messages.showErrorDialog( list.get( 0 ).getLocalizedMessage(), "Error" );
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

  public static TransparentVcs getHost( AnActionEvent e )
  {
    return TransparentVcs.getInstance( e.getData( DataKeys.PROJECT ) );
  }
}
