package net.sourceforge.transparent.History;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.ColumnInfo;
import net.sourceforge.transparent.StatusMultipleProcessor;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CCaseHistoryProvider implements VcsHistoryProvider, VcsCacheableHistorySessionFactory<Boolean, CCaseHistoryProvider.CCaseHistorySession> {
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
      return (o1, o2) -> {
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

  public boolean supportsHistoryForDirectories() {
    return false;
  }

  @Override
  public DiffFromHistoryHandler getHistoryDiffHandler() {
    return null;
  }

  @Override
  public boolean canShowHistoryFor(@NotNull VirtualFile file) {
    return true;
  }

  public AnAction[]   getAdditionalActions(final Runnable refresher) {  return AnAction.EMPTY_ARRAY;   }
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

    final List<String> commandParts = new ArrayList<>();
    commandParts.add(HISTORY_CMD);
    if (host.getConfig().isHistoryResticted) {
      int margin = host.getConfig().getHistoryRevisionsMargin();
      commandParts.add(LIMITED_SWITCH);
      commandParts.add(String.valueOf( margin ));
    }
    CCaseHistoryParser.fillParametersTail(commandParts);
    commandParts.add(path);

    log = TransparentVcs.cleartoolWithOutput(ArrayUtil.toStringArray(commandParts));

    //  There may exist files for which we know nothing.
    ArrayList<VcsFileRevision> revisions = new ArrayList<>();
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
          VcsFileRevision rev = new CCaseFileRevision(change, path, project);
          revisions.add( rev );
        }
        catch( NullPointerException e)
        {
          TransparentVcs.LOG.info( "Can not parse history record, found intermediate record.");
        }
      }
    }

    return new CCaseHistorySession(revisions, filePath);
  }

  public static void historyGetter(final Project project, final FilePath filePath, final int maxCnt,
                                   final Consumer<CCaseHistoryParser.SubmissionData> consumer) throws VcsException {
    final TransparentVcs host = TransparentVcs.getInstance(project);
    String log;
    String path = filePath.getPath();
    if(host.renamedFiles.containsKey( path ) )
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

    final List<String> commandParts = new ArrayList<>();
    commandParts.add(HISTORY_CMD);
    if (maxCnt > 0) {
      commandParts.add(LIMITED_SWITCH);
      commandParts.add(String.valueOf(maxCnt));
    } else if (host.getConfig().isHistoryResticted) {
      int margin = host.getConfig().getHistoryRevisionsMargin();
      commandParts.add(LIMITED_SWITCH);
      commandParts.add(String.valueOf( margin ));
    }
    CCaseHistoryParser.fillParametersTail(commandParts);
    commandParts.add(path);

    log = TransparentVcs.cleartoolWithOutput(ArrayUtil.toStringArray(commandParts));

    if( log.contains( NOT_A_VOB_OBJECT )) {
      throw new VcsException( log );
    } else {
      ArrayList<CCaseHistoryParser.SubmissionData> changes = CCaseHistoryParser.parse( log );
      for (CCaseHistoryParser.SubmissionData change : changes) {
        consumer.consume(change);
      }
    }
  }

  public void reportAppendableHistory(FilePath path, VcsAppendableHistorySessionPartner partner) throws VcsException {
    // will implement it further
    final VcsHistorySession session = createSessionFor(path);
    partner.reportCreatedEmptySession((VcsAbstractHistorySession) session);
  }

  public FilePath getUsedFilePath(CCaseHistorySession session) {
    return null;
  }

  public CCaseHistorySession createFromCachedData(@Nullable Boolean aBoolean,
                                                  @NotNull List<VcsFileRevision> revisions,
                                                  @NotNull FilePath filePath,
                                                  @Nullable VcsRevisionNumber currentRevision) {
    return new CCaseHistorySession(revisions, filePath);
  }

  static class CCaseHistorySession extends VcsAbstractHistorySession
  {
    private final FilePath myPath;

    public CCaseHistorySession( List<VcsFileRevision> revs, final FilePath path )
    {
      super( revs , currentRevisionImpl(path, revs));
      myPath = path;
    }

    public boolean isContentAvailable(VcsFileRevision revision)
    {
      return (revision instanceof CCaseFileRevision) &&
             !(CCaseHistoryParser.BRANCH_COMMAND_SIG.equals(((CCaseFileRevision)revision).getAction())) &&
             !(CCaseHistoryParser.CREATE_ELEM_COMMAND_SIG.equals(((CCaseFileRevision)revision).getAction())); 
    }

    protected VcsRevisionNumber calcCurrentRevisionNumber() {
      return currentRevisionImpl(myPath, getRevisionList());
    }

    private static VcsRevisionNumber currentRevisionImpl(final FilePath filePath, final List<VcsFileRevision> list) {
      final String currentRevision = StatusMultipleProcessor.getCurrentRevision(filePath.getPath());
      if (currentRevision != null) {
        for (VcsFileRevision revision : list) {
          if (revision.getRevisionNumber().asString().equals(currentRevision)) {
            return revision.getRevisionNumber();
          }
        }
        return new CCaseRevisionNumber(currentRevision, 0);
      }

      VcsRevisionNumber revision;
      try
      {
        int maxRevision = 0;
        for( VcsFileRevision rev : list )
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

    @Override
    public boolean isCurrentRevision(VcsRevisionNumber rev) {
      final VcsRevisionNumber cachedRevision = getCachedRevision();
      return cachedRevision != null && cachedRevision.asString().equals(rev.asString());
    }

    public HistoryAsTreeProvider getHistoryAsTreeProvider() {
      return null;
    }

    @Override
    public VcsHistorySession copy() {
      return new CCaseHistorySession(getRevisionList(), myPath);
    }
  }
}
