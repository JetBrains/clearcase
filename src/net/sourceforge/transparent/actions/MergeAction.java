package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Apr 5, 2007
 */
public class MergeAction extends AsynchronousAction
{
  @NonNls private final static String ACTION_NAME = "Merge file...";
  @NonNls private final static String VERSION_SIG = "version \"";
  @NonNls private final static String ERROR_TEXT = "<findmerge> can not find a version for merging in the VOB.";
  @NonNls private final static String ERROR_TITLE = "Merge Failed";

  protected String getActionName( AnActionEvent e ) { return ACTION_NAME; }

  public void perform(final VirtualFile file, final Project project)
  {
    final String path = file.getPath().replace( '/', File.separatorChar );
    String findVerOut = TransparentVcs.cleartoolWithOutput( "findmerge", path, "-flatest", "-print", "-long" );
    if( StringUtil.isNotEmpty( findVerOut ))
    {
      final String elementVersion = extractVersion( findVerOut );
      ApplicationManager.getApplication().executeOnPooledThread( new Runnable() {
        public void run()
        {
          TransparentVcs.cleartoolWithOutput( "merge", "-g", "-to", path, elementVersion );

          file.putUserData( TransparentVcs.MERGE_CONFLICT, null );
          file.refresh( false, false );
          VcsUtil.markFileAsDirty( project, file );
        }
      });
    }
    else
    {
      AbstractVcsHelper.getInstance( project ).showError( new VcsException( ERROR_TEXT ), ERROR_TITLE );
    }
  }

  protected boolean isEnabled(VirtualFile file, final Project project)
  {
    FileStatus status = getFileStatus( project, file );
    return status == FileStatus.MERGED_WITH_CONFLICTS;
  }

  private static String extractVersion( String findVerOut )
  {
    String version = "";
    String[] lines = LineTokenizer.tokenize( findVerOut, false );
    for( String line : lines )
    {
      if( line.startsWith( VERSION_SIG ))
      {
        version = line.substring( VERSION_SIG.length(), line.length() - 1 );
        break;
      }
    }
    return version;
  }
}
