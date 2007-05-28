package net.sourceforge.transparent.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
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

  public void perform( VirtualFile file, AnActionEvent e )
  {
    String path = file.getPath().replace( '/', File.separatorChar ); 
    String findVerOut = TransparentVcs.cleartoolWithOutput( "findmerge", path, "-flatest", "-print", "-long" );
    if( StringUtil.isNotEmpty( findVerOut ))
    {
      String elementVersion = extractVersion( findVerOut );
      cleartool( "merge", "-g", "-to", path, elementVersion );
    }
    else
    {
      Messages.showErrorDialog( getProject( e ), ERROR_TEXT, ERROR_TITLE );
    }
  }

  protected boolean isEnabled( VirtualFile file, AnActionEvent e )
  {
    FileStatus status = getFileStatus( e.getData( DataKeys.PROJECT ), file );
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
