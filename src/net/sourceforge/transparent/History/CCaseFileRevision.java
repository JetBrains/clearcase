package net.sourceforge.transparent.History;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vcsUtil.VcsImplUtil;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
* @author irengrig
*         Date: 3/28/11
*         Time: 3:32 PM
*/
public class CCaseFileRevision implements VcsFileRevision
{
  private final String version;
  private final String submitter;
  private final String changeCcaseDate;
  private final String comment;
  private final String action;
  private final String labels;
  private final int    order;

  private final String path;
  private byte[] content;
  private final Project myProject;

  public CCaseFileRevision(CCaseHistoryParser.SubmissionData data, String path, final Project project) {
    myProject = project;
    version = data.version;
    submitter = data.submitter;
    comment = data.comment;
    action = data.action;
    labels = data.labels;
    order = data.order;
    changeCcaseDate = data.changeDate;

    this.path = path;
  }

  @Nullable
  @Override
  public RepositoryLocation getChangedRepositoryPath() {
    return null;
  }

  public byte[] getContent() { return content;    }
  public String getBranchName()   { return null;       }
  public Date getRevisionDate() { return null; }
  public String getChangeCcaseDate() { return changeCcaseDate; }
  public String getAuthor()       { return submitter;  }
  public String getCommitMessage(){ return comment;    }
  public int    getOrder()        { return order;      }
  public String getAction()       { return action;     }
  public String getLabels()       { return labels;     }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {  return new CCaseRevisionNumber(version, order );  }

  public byte[] loadContent() {
    @NonNls final String TMP_FILE_NAME = "idea_ccase";
    @NonNls final String EXT = ".tmp";
    @NonNls final String TITLE = "Can not issue Get command";

    try
    {
      File tmpFile = FileUtil.createTempFile(TMP_FILE_NAME, EXT);
      tmpFile.deleteOnExit();
      File tmpDir = tmpFile.getParentFile();
      final File myTmpFile = new File( tmpDir, Long.toString( new Date().getTime()) );
      myTmpFile.deleteOnExit();

      final String out = TransparentVcs.cleartoolWithOutput("get", "-to", myTmpFile.getPath(), path + version);

      //  We expect that properly finished command produce no (error or
      //  warning) output.
      if( out.length() > 0 ) {
        VcsImplUtil.showErrorMessage(myProject, out, TITLE);
      } else {
        content = VcsUtil.getFileByteContent( myTmpFile );
      }
    }
    catch( IOException e )
    {
      content = null;
    }
    return content;
  }

  public int compareTo( Object revision )
  {
    return getRevisionNumber().compareTo( ((CCaseFileRevision)revision).getRevisionNumber() );
  }
}
