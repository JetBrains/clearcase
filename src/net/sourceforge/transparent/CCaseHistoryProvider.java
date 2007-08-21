package net.sourceforge.transparent;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Jan 26, 2007
 */
public class CCaseHistoryProvider implements VcsHistoryProvider
{
  @NonNls private final static String HISTORY_CMD = "lshistory";
  @NonNls private final static String LIMITED_SWITCH = "-last";
  @NonNls private final static String HIJACKED_SIG = "[hijacked]";
  @NonNls private final static String CCASE_DATE_COLUMN = "ClearCase Date";
  @NonNls private final static String ACTION_COLUMN = "Action";
  @NonNls private final static String LABEL_COLUMN = "Label";

  @NonNls private final static String HISTORY_FAILED_MSG = "Can not extract history records for a hijacked file.";
  @NonNls private final static String HISTORY_FAILED_TITLE = "Getting History Failed";

  private Project project;
  private TransparentVcs host;

  public CCaseHistoryProvider( Project project )
  {
    this.project = project;
    host = TransparentVcs.getInstance( project );
  }

  private static final ColumnInfo<VcsFileRevision, String> CCASE_DATE = new ColumnInfo<VcsFileRevision, String>( CCASE_DATE_COLUMN )
  {
    public String valueOf( VcsFileRevision revision ) {
      if (!(revision instanceof CCaseFileRevision)) return "";
      return ((CCaseFileRevision) revision).getChangeCcaseDate();
    }

    public Comparator<VcsFileRevision> getComparator()
    {
      return new Comparator<VcsFileRevision>() {
        public int compare(VcsFileRevision o1, VcsFileRevision o2)
        {
          if (!(o1 instanceof CCaseFileRevision)) return 0;
          if (!(o2 instanceof CCaseFileRevision)) return 0;

          CCaseFileRevision cO1 = (CCaseFileRevision) o1;
          CCaseFileRevision cO2 = (CCaseFileRevision) o2;
          if( cO1.getOrder() < cO2.getOrder() )
            return -1;
          else
          if( cO1.getOrder() > cO2.getOrder() )
            return 1;
          return 0;
        }
      };
    }
  };

  private static final ColumnInfo<VcsFileRevision, String> ACTION = new ColumnInfo<VcsFileRevision, String>( ACTION_COLUMN )
  {
    public String valueOf(VcsFileRevision vcsFileRevision) {
      if (!(vcsFileRevision instanceof CCaseFileRevision)) return "";
      return ((CCaseFileRevision) vcsFileRevision).getAction();
    }
  };

  private static final ColumnInfo<VcsFileRevision, String> LABEL = new ColumnInfo<VcsFileRevision, String>( LABEL_COLUMN )
  {
    public String valueOf(VcsFileRevision vcsFileRevision) {
      if (!(vcsFileRevision instanceof CCaseFileRevision)) return "";
      return ((CCaseFileRevision) vcsFileRevision).getLabels();
    }
  };

  public boolean isDateOmittable() {  return true;  }

  @NonNls @Nullable
  public String getHelpId() {  return null;  }

  @Nullable
  public HistoryAsTreeProvider getTreeHistoryProvider() {  return null;   }
  public AnAction[]   getAdditionalActions(final FileHistoryPanel panel) {  return new AnAction[0];   }
  public ColumnInfo[] getRevisionColumns()  {  return new ColumnInfo[] { CCASE_DATE, ACTION, LABEL };  }

  public VcsHistorySession createSessionFor( FilePath filePath ) throws VcsException
  {
    String log;
    String path = filePath.getPath();
    if( host.renamedFiles.containsKey( path ) )
      path = host.renamedFiles.get( path );

    //  Cleartool can not handle history for hijacked files. Thus we have to
    //  extract the version id corresponding to the VOB object and retrieve
    //  the history for that version.
    VirtualFile vfile = filePath.getVirtualFile();
    if( vfile != null )
    {
      FileStatus status = FileStatusManager.getInstance( project ).getStatus( vfile );
      if( status == FileStatus.HIJACKED )
      {
        log = TransparentVcs.cleartoolWithOutput( "ls", path );
        int index = log.indexOf( HIJACKED_SIG );
        if( index != -1 )
        {
          path = log.substring( 0, index - 1 ).trim();
        }
      }
    }

    if( host.getConfig().isHistoryResticted )
    {
      int margin = host.getConfig().getHistoryRevisionsMargin();
      log = TransparentVcs.cleartoolWithOutput( HISTORY_CMD, LIMITED_SWITCH, String.valueOf( margin ), path );
    }
    else
    {
      log = TransparentVcs.cleartoolWithOutput( HISTORY_CMD, path );
    }
    ArrayList<CCaseHistoryParser.SubmissionData> changes = CCaseHistoryParser.parse( log );
    ArrayList<VcsFileRevision> revisions = new ArrayList<VcsFileRevision>();
    for( CCaseHistoryParser.SubmissionData change : changes )
    {
      //  When file is being committed into the repository, "lshistory"
      //  returns a intermediate record with commit date in the invalid format
      //  which can not be parsed (actually, it contains only full date wihtout
      //  time information delimited by '.'). Just skip this record.
      try
      {
        VcsFileRevision rev = new CCaseFileRevision( change, path );
        revisions.add( rev );
      }
      catch( NullPointerException e)
      {
        TransparentVcs.LOG.info( "Can not parse history record, found intermediate record.");
      }
    }

    return new CCaseHistorySession( revisions );
  }

  private class CCaseFileRevision implements VcsFileRevision
  {
    private String version;
    private String submitter;
    private String changeCcaseDate;
    private String comment;
    private String action;
    private String labels;
    private int    order;

    private String path;
    private byte[] content;

    public CCaseFileRevision( CCaseHistoryParser.SubmissionData data, String path )
    {
      version = data.version;
      submitter = data.submitter;
      comment = data.comment;
      action = data.action;
      labels = data.labels;
      order = data.order;
      changeCcaseDate = data.changeDate;

      this.path = path;
    }

    public byte[] getContent()      { return content;    }
    public String getBranchName()   { return null;       }
    public Date   getRevisionDate() { return null; }
    public String getChangeCcaseDate() { return changeCcaseDate; }
    public String getAuthor()       { return submitter;  }
    public String getCommitMessage(){ return comment;    }
    public int    getOrder()        { return order;      }
    public String getAction()       { return action;     }
    public String getLabels()       { return labels;     }

    public VcsRevisionNumber getRevisionNumber() {  return new CCaseRevisionNumber( version, order );  }

    public void loadContent()
    {
      @NonNls final String TMP_FILE_NAME = "idea_ccase";
      @NonNls final String EXT = ".tmp";
      @NonNls final String TITLE = "Can not issue Get command";

      try
      {
        File tmpFile = File.createTempFile( TMP_FILE_NAME, EXT );
        tmpFile.deleteOnExit();
        File tmpDir = tmpFile.getParentFile();
        final File myTmpFile = new File( tmpDir, Long.toString( new Date().getTime()) );
        myTmpFile.deleteOnExit();

        final String out = TransparentVcs.cleartoolWithOutput( "get", "-to", myTmpFile.getPath(), path + version );

        //  We expect that properly finished command produce no (error or
        //  warning) output.
        if( out.length() > 0 )
        {
          VcsUtil.showErrorMessage( project, out, TITLE );
        }
        else
        {
          content = VcsUtil.getFileByteContent( myTmpFile );
        }
      }
      catch( IOException e )
      {
        content = null;
      }
    }

    public int compareTo( Object revision )
    {
      return getRevisionNumber().compareTo( ((CCaseFileRevision)revision).getRevisionNumber() );
    }
  }

  private static class CCaseHistorySession extends VcsHistorySession
  {
    public CCaseHistorySession( ArrayList<VcsFileRevision> revs )
    {
      super( revs );
    }

    public boolean isContentAvailable(VcsFileRevision revision)
    {
      return (revision instanceof CCaseFileRevision) &&
             !((CCaseFileRevision)revision).getAction().equals( CCaseHistoryParser.BRANCH_COMMAND_SIG ) &&
             !((CCaseFileRevision)revision).getAction().equals( CCaseHistoryParser.CREATE_ELEM_COMMAND_SIG ); 
    }

    protected VcsRevisionNumber calcCurrentRevisionNumber()
    {
      VcsRevisionNumber revision;
      try
      {
        int maxRevision = 0;
        for( VcsFileRevision rev : getRevisionList() )
        {
          maxRevision = Math.max( maxRevision, ((CCaseFileRevision)rev).getOrder() );
        }
        revision = new VcsRevisionNumber.Int( maxRevision );
      }
      catch( Exception e )
      {
        //  We can catch e.g. com.starbase.starteam.ItemNotFoundException if we
        //  try to show history records for the deleted file.
        revision = VcsRevisionNumber.NULL;
      }
      return revision;
    }
  }

  private class CCaseRevisionNumber implements VcsRevisionNumber
  {
    private String revision;
    private int    order;

    public CCaseRevisionNumber( String revision, int order )
    {
      this.revision = revision;
      this.order = order;
    }

    public String asString() { return revision;  }

    public int compareTo( VcsRevisionNumber revisionNumber )
    {
      CCaseRevisionNumber rev = (CCaseRevisionNumber)revisionNumber;
      if( order > rev.order )
        return -1;
      else
      if( order < rev.order )
        return 1;
      else
        return 0;
    }
  }
}
