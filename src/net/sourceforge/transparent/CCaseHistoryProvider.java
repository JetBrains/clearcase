package net.sourceforge.transparent;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Jan 26, 2007
 * Time: 2:14:24 PM
 * To change this template use File | Settings | File Templates.
 */
public class CCaseHistoryProvider implements VcsHistoryProvider
{
  @NonNls private final static String ACTION_COLUMN = "Action";
  @NonNls private final static String LABEL_COLUMN = "Label";
  @NonNls private final static String ERROR_TITLE = "Get History Fail";

  private Project project;

  public CCaseHistoryProvider( Project project )
  {
    this.project = project;
  }

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


  @NonNls @Nullable
  public String getHelpId() {  return null;  }

  @Nullable
  public HistoryAsTreeProvider getTreeHistoryProvider() {  return null;   }
  public AnAction[]   getAdditionalActions(final FileHistoryPanel panel) {  return new AnAction[0];   }
  public ColumnInfo[] getRevisionColumns()  {  return new ColumnInfo[] { ACTION, LABEL };  }

  public VcsHistorySession createSessionFor( FilePath filePath ) throws VcsException
  {
    CCaseHistorySession session = null;
    try
    {
      String log = TransparentVcs.cleartoolWithOutput( "lshistory", filePath.getPath() );
      ArrayList<CCaseHistoryParser.SubmissionData> changes = CCaseHistoryParser.parse( log );
      ArrayList<VcsFileRevision> revisions = new ArrayList<VcsFileRevision>();
      for( CCaseHistoryParser.SubmissionData change : changes )
      {
        VcsFileRevision rev = new CCaseFileRevision( change, filePath );
        revisions.add( rev );
      }

      session = new CCaseHistorySession( revisions );
    }
    catch( ClearCaseException e )
    {
      throw new VcsException( e );
    }
    return session;
  }

  private class CCaseFileRevision implements VcsFileRevision
  {
    private String version;
    private String submitter;
    private Date changeDate;
    private String comment;
    private String action;
    private String labels;
    private int    order;

    private FilePath path;
    private byte[] content;

    public CCaseFileRevision( CCaseHistoryParser.SubmissionData data, FilePath path )
    {
      version = data.version;
      submitter = data.submitter;
      comment = data.comment;
      action = data.action;
      labels = data.labels;
      changeDate = new Date();
      order = data.order;

      //  Parse separately date and time using several simple heuristics.
      long dateValue = parseDate( data.changeDate );
      long timeValue = parseTime( data.changeDate );
      changeDate.setTime( dateValue + timeValue );

      this.path = path;
    }

    public byte[] getContent()      { return content;    }
    public String getBranchName()   { return null;       }
    public Date   getRevisionDate() { return changeDate; }
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

        final String out = TransparentVcs.cleartoolWithOutput( "get", "-to", myTmpFile.getPath(), path.getPath() + version );

        //  We expect that properly finished command produce no (error or
        //  warning) output.
        if( out.length() > 0 )
        {
          ApplicationManager.getApplication().invokeLater( new Runnable() { public void run() { VcsUtil.showErrorMessage( project, out, TITLE ); } });
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

    private long parseDate( String dateStr )
    {
      Locale locale = Locale.getDefault();
      long dateValue = 0;

      try
      {
        DateFormat df = DateFormat.getDateInstance( DateFormat.SHORT, locale );
        dateValue = df.parse( dateStr ).getTime();
      }
      catch( ParseException e )
      {
        //  Hack: when user modifies an existing locale, these changes are
        //  not propagated into ResourceBundle which is used by SimpleDateFormat
        //  stuff. Prepare seveal more commonly used and try to precess them
        //  separately.
        //  Todo: move a format into IDEA property?

        @NonNls String[] dateFormats = new String[] { "d-M-y", "d-M-yy", "d-MM-yy", "d-M-yyyy", "d-MM-yyyy", "M/d/y", "M.d.y", "d.M.y", "d.MM.yy", "M/d/yy", "M.d.yy", "d.M.yy" };

        for( String format : dateFormats )
        {
          try
          {
            DateFormat df = new SimpleDateFormat( format );
            dateValue = df.parse( dateStr ).getTime();
            break;
          }
          catch( ParseException e2 ){  dateValue = 0;  }
        }
      }
      return dateValue;
    }

    private long parseTime( String time )
    {
      Locale locale = Locale.getDefault();
      long timeValue = 0;

      try
      {
        DateFormat tf = DateFormat.getTimeInstance( DateFormat.SHORT, locale );
        timeValue = tf.parse( time ).getTime();
      }
      catch( ParseException e )
      {
        //  Hack: Same as in the case of Date value parsing.
        @NonNls String[] formats = new String[] { "h:mm a", "H:mm", "hh:mm a", "HH:mm" };

        for( String format : formats )
        {
          try
          {
            DateFormat tf = new SimpleDateFormat( format );
            timeValue = tf.parse( time ).getTime();
            break;
          }
          catch( ParseException e2 ) {  timeValue = 0;  }
        }
      }
      return timeValue;
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
        revision = new VcsRevisionNumber.Int( maxRevision + 1 );
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
