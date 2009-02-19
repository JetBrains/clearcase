package net.sourceforge.transparent.ChangeManagement;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.History.CCaseHistoryParser;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
* User: lloix
* Date: Feb 21, 2007
*/
public class CCaseContentRevision implements ContentRevision
{
  @NonNls private static final String TMP_FILE_NAME = "idea_ccase";
  @NonNls private static final String VERSION_SEPARATOR = "@@";
  @NonNls private static final String WARNING_NO_GROUP_SIG = "Warning: Can not find a group named";

  private final VirtualFile   file;
  private final FilePath      revisionPath;
  private final Project       project;
  private String        myServerContent;
  private final TransparentVcs host;

  public CCaseContentRevision( FilePath path, Project proj )
  {
    revisionPath = path;
    project = proj;

    host = TransparentVcs.getInstance( proj );
    file = path.getVirtualFile();
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber()  {  return VcsRevisionNumber.NULL;   }
  @NotNull
  public FilePath getFile()                     {  return revisionPath; }

  public String getContent()
  {
    if( myServerContent == null )
      myServerContent = getServerContent();

    return myServerContent;
  }

  private String getServerContent()
  {
    @NonNls final String TITLE = "Error";
    @NonNls final String EXT = ".tmp";
    String content = "";

    //  For files which are in the project but reside outside the repository
    //  root their base revision version content is not defined (empty).

    if( host.fileIsUnderVcs( revisionPath ))
    {
      //  If we are in the offline mode, no cleartool commands are allowed,
      //  and the file conent is what we have currently in the VFS.
      if( host.getConfig().isOffline )
      {
        if( file != null )
        {
          try { content = CharsetToolkit.bytesToString(file.contentsToByteArray()); }
          catch( IOException e ){ /* nothing to do, content remains empty */ }
        }
      }
      else
      {
        try
        {
          //-------------------------------------------------------------------
          //  Since CCase does not allow us to get the latest content of a file
          //  from the repository, we need to get the VERSION string which
          //  characterizes this latest (or any other) version.
          //  Using this version string we can construct actual request to the
          //  "Get" command:
          //  "get -to <dest_file> <repository_file>@@<version>"
          //-------------------------------------------------------------------

          File tmpFile = File.createTempFile( TMP_FILE_NAME, EXT );
          tmpFile.deleteOnExit();
          File tmpDir = tmpFile.getParentFile();
          File myTmpFile = new File( tmpDir, Long.toString( new Date().getTime()) );

          String version = null;
          FileStatusManager mgr = FileStatusManager.getInstance( project );

          if( file == null )
          {
            String out = TransparentVcs.cleartoolWithOutput( "describe", revisionPath.getPath() );
            version = parseLastRepositoryVersion( out );
          }
          else
          //---------------------------------------------------------------------
          //  We need to explicitely distinguish between normal (checked out)
          //  files and hijacked files - CCase treats the latter as "private files"
          //  (with respect to the view) and the "Describe" command does not
          //  return VOB-object-specific information. "History" command also
          //  does not work for this file if only we did not specify "@@" at
          //  the end, explicitely telling that we are interesting in the
          //  repository object. In this case we need only to extract the version
          //  identifier latest in the hitory (first record).
          //---------------------------------------------------------------------
          if( mgr.getStatus( file ) == FileStatus.HIJACKED )
          {
            final List<String> commandParts = new ArrayList<String>();
            commandParts.add("lshistory");
            CCaseHistoryParser.fillParametersVersionOnly(commandParts);
            commandParts.add(file.getPath() + VERSION_SEPARATOR);
            String log = TransparentVcs.cleartoolWithOutput( commandParts.toArray(new String[commandParts.size()]) );
            ArrayList<CCaseHistoryParser.SubmissionData> changes = CCaseHistoryParser.parse( log );
            if( changes.size() > 0 )
            {
              version = changes.get( 0 ).version;

              //  do not forget to strip "@@"
              if( version.startsWith( VERSION_SEPARATOR ))
                version = version.substring( 2 );
            }
          }
          else
          {
            String out = TransparentVcs.cleartoolWithOutput( "describe", file.getPath() );
            version = parseLastRepositoryVersion( out );
          }

          if( version != null )
          {
            String path = VcsUtil.getCanonicalLocalPath( revisionPath.getPath() );
            final String out2 = TransparentVcs.cleartoolWithOutput( "get", "-to", myTmpFile.getPath(), path + VERSION_SEPARATOR + version );

            //  We expect that properly finished command produce no (error or
            //  warning) output. The only messages allowed are the warnings from
            //  other subsystems which are not related to the "ct get" command per se.
            if( out2.length() > 0 && !isKnownMessage( out2 ) )
            {
              ApplicationManager.getApplication().invokeLater( new Runnable() { public void run() { VcsUtil.showErrorMessage( project, out2, TITLE ); } });
            }
            else
            {
              byte[] byteContent = VcsUtil.getFileByteContent( myTmpFile );
              content = CharsetToolkit.bytesToString(byteContent);
              myTmpFile.delete();
            }
          }
        }
        catch( Exception e )
        {
           VcsUtil.showErrorMessage( project, e.getMessage(), TITLE );
        }
      }
    }

    return content;
  }

  private static boolean isKnownMessage( final String message )
  {
    return message.contains( WARNING_NO_GROUP_SIG );
  }
  
  //-------------------------------------------------------------------------
  //  The sample format of the "DESCRIBE" command output is as follows below:
  //  --
  //  version "Foo22.java@@\main\p1_Integration\lloix_p1\CHECKEDOUT" from \main\p1_Integration\lloix_p1\0 (unreserved)
  //    checked out 19-Jan-07.12:53:23 by lloix.Domain Users@greYWolf
  //    by view: lloix_IrinaVobProjects ("GREYWOLF:D:\Projects\Test Projects\cc-tut\lloix_snapview2\lloix_IrinaVobProjects.vws")
  //    "sss"
  //    Element Protection:
  //      User : LABS\Irina.Petrovskaya : r--
  //      Group: LABS\Domain Users : r--
  //      Other:          : r--
  //    element type: text_file
  //    predecessor version: \main\p1_Integration\lloix_p1\0
  //    Attached activities:
  //      activity:Added@\irinaVOB  "Test all operations in Cleartool mode"
  //  --
  //  In order to retrieve the latest version in the repository, we seek for
  //  string "predecessor version:" in this log and extract the version string
  //  in the CCase format.
  //-------------------------------------------------------------------------
  @Nullable
  private static String parseLastRepositoryVersion( String text )
  {
    @NonNls final String SIG = "predecessor version:";
    String version = null;
    String[] lines = text.split( "\n" );
    for( String line : lines )
    {
      int index = line.indexOf( SIG );
      if( index != -1 )
      {
        version = line.substring( index + SIG.length() ).trim();
        break;
      }
    }
    return version;
  }
}
