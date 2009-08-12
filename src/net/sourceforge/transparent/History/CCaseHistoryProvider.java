package net.sourceforge.transparent.History;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Jan 26, 2007
 */
public class CCaseHistoryProvider implements VcsHistoryProvider
{
  @NonNls private final static String HISTORY_CMD = "lshistory";
  @NonNls private final static String LIMITED_SWITCH = "-last";
  @NonNls private final static String CCASE_DATE_COLUMN = "ClearCase Date";
  @NonNls private final static String ACTION_COLUMN = "Action";
  @NonNls private final static String LABEL_COLUMN = "Label";

  @NonNls private final static String NOT_A_VOB_OBJECT = "Not a vob object";

  private final Project project;
  private final TransparentVcs host;

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

  public boolean supportsHistoryForDirectories() {
    return false;
  }

  public AnAction[]   getAdditionalActions(final FileHistoryPanel panel) {  return AnAction.EMPTY_ARRAY;   }
  public VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, JComponent forShortcutRegistration)  {  return VcsDependentHistoryComponents.createOnlyColumns(new ColumnInfo[] { CCASE_DATE, ACTION, LABEL });  }

  public VcsHistorySession createSessionFor( FilePath filePath ) throws VcsException
  {
    String log;
    String path = filePath.getPath();
    if( host.renamedFiles.containsKey( path ) )
      path = host.renamedFiles.get( path );

    //  Cleartool can not handle history for hijacked files. Thus we have to
    //  construct "versioned" path, which directly points to the vob-object.
    VirtualFile vfile = filePath.getVirtualFile();
    if( vfile != null )
    {
      FileStatus status = FileStatusManager.getInstance( project ).getStatus( vfile );
      if( status == FileStatus.HIJACKED )
      {
        path += "@@";
      }
    }

    final List<String> commandParts = new ArrayList<String>();
    commandParts.add(HISTORY_CMD);
    if (host.getConfig().isHistoryResticted) {
      int margin = host.getConfig().getHistoryRevisionsMargin();
      commandParts.add(LIMITED_SWITCH);
      commandParts.add(String.valueOf( margin ));
    }
    CCaseHistoryParser.fillParametersTail(commandParts);
    commandParts.add(path);

    log = TransparentVcs.cleartoolWithOutput(commandParts.toArray(new String [commandParts.size()]));

    //  There may exist files for which we know nothing.
    ArrayList<VcsFileRevision> revisions = new ArrayList<VcsFileRevision>();
    if( log.contains( NOT_A_VOB_OBJECT )) {
      throw new VcsException( log );
    } else {
      ArrayList<CCaseHistoryParser.SubmissionData> changes = CCaseHistoryParser.parse( log );
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
    }

    return new CCaseHistorySession( revisions );
  }

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
             !(CCaseHistoryParser.BRANCH_COMMAND_SIG.equals(((CCaseFileRevision)revision).getAction())) &&
             !(CCaseHistoryParser.CREATE_ELEM_COMMAND_SIG.equals(((CCaseFileRevision)revision).getAction())); 
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
    private final String revision;
    private final int    order;

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
