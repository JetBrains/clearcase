/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package net.sourceforge.transparent.Checkin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.CCaseViewsManager;
import net.sourceforge.transparent.ClearCase;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Dec 6, 2006
 */
public class CCaseCheckinEnvironment implements CheckinEnvironment
{
  @NonNls private static final String CHECKIN_TITLE = "Check In";
  @NonNls private static final String SCR_TITLE = "SCR Number";

  @NonNls private static final String CHECKOUT_FOLDER = "Checking out folder: ";
  @NonNls private static final String ADDING_FILES = "Adding file: ";
  @NonNls private static final String CHECKIN_FOLDER = "Checking in folder: ";
  @NonNls private static final String CHANGE_ACTIVITY = "Changing activity for file: ";

  private final Project project;
  private final TransparentVcs host;
  private double fraction;
  private String submittedChangeListName;

  public CCaseCheckinEnvironment( Project project, TransparentVcs host )
  {
    this.project = project;
    this.host = host;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel( CheckinProjectPanel panel )
  {
    @NonNls final JPanel additionalPanel = new JPanel();
    final JTextField scrNumber = new JTextField();

    additionalPanel.setLayout( new BorderLayout() );
    additionalPanel.add( new Label( SCR_TITLE ), "North" );
    additionalPanel.add( scrNumber, "Center" );

    scrNumber.addFocusListener( new FocusListener()
    {
        public void focusGained(FocusEvent e) {  scrNumber.selectAll();  }
        public void focusLost(FocusEvent focusevent) {}
    });

    return new RefreshableOnComponent()
    {
      public JComponent getComponent() {  return additionalPanel;  }

      public void saveState() { }
      public void restoreState() {  refresh();   }
      public void refresh() { }
    };
  }

  /**
   * Force to reuse the last checkout's comment for the checkin.
   */
  public String getDefaultMessageFor( FilePath[] filesToCheckin )
  {
    ClearCase cc = host.getClearCase();
    HashSet<String> commentsPerFile = new HashSet<String>();
    for( FilePath path : filesToCheckin )
    {
      //  For ADDED or DELETED files checkout comment has no sence.
      //  While DELETED status is determined indirectly (its VirtualFile is null),
      //  for ADDED we need to ask.
      VirtualFile vfile = path.getVirtualFile();
      if( vfile != null )
      {
        FileStatus status = FileStatusManager.getInstance(project).getStatus( vfile );
        if( status != FileStatus.ADDED )
        {
          String fileComment = cc.getCheckoutComment( new File( path.getPresentableUrl() ) );
          if( StringUtil.isNotEmpty( fileComment ) )
            commentsPerFile.add( fileComment );
        }
      }
    }

    StringBuilder overallComment = new StringBuilder();
    for( String comment : commentsPerFile )
    {
      overallComment.append( comment ).append( "\n-----" );
    }

    //  If Checkout comment is empty - return null, in this case <caller> will
    //  inherit last commit's message for this commit.
    return (overallComment.length() > 0) ? overallComment.toString() : null;
  }


  public String  getHelpId() {  return null;   }
  public String  getCheckinOperationName() {  return CHECKIN_TITLE;  }
  public boolean keepChangeListAfterCommit( ChangeList changeList )
  {
    return true;
  }

  public List<VcsException> commit( List<Change> changes, String comment )
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    HashSet<FilePath> processedFiles = new HashSet<FilePath>();
    comment = comment.replace( "\"", "\\\"" );

    clearTemporaryStatuses( changes );
    storeChangeListName( changes );

    adjustChangesWithRenamedParentFolders( changes );

    try
    {
      initProgress( changes.size() );

      //  Committing of renamed folders must be performed first since they
      //  affect all other checkings under them (except those having status
      //  "ADDED") since:
      //  - if modified file is checked in before renamed folder checkin then
      //    we need to checkin from (yet) nonexisting file into (already) non-
      //    existing space. It is too tricky to recreate the old folders
      //    structure and commit from out of there.
      //  - if modified file is checked AFTER the renamed folder has been
      //    checked in, we just have to checkin in into the necessary place,
      //    just get the warning that we checking in file which was checked out
      //    from another location. Supress it.
      commitRenamedFolders( changes, comment, errors );

      commitDeleted( changes, comment, errors );

      //  IMPORTANT!
      //  Committment of the changed files must be performed first because of
      //  specially processed exceptions described in the ChangeProvider.
      commitChanged( changes, comment, processedFiles, errors );
      commitNew( changes, comment, processedFiles, errors );
    }
    catch( ProcessCanceledException e )
    {
      //  Nothing to do, just refresh the files which have been already committed.
    }

    VcsUtil.refreshFiles( project, processedFiles );

    return errors;
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment, Object parameters) {
    return commit(changes, preparedComment);
  }

  /**
   * Before new commit, clear all "Merge Conflict" statuses on files set on
   * them since the last commit. 
   */
  private static void clearTemporaryStatuses( List<Change> changes )
  {
    for( Change change : changes )
    {
      ContentRevision rev = change.getAfterRevision();
      if( rev != null )
      {
        FilePath filePath = rev.getFile();
        VirtualFile file = filePath.getVirtualFile();
        if( file != null ) //  e.g. for deleted files
          file.putUserData( TransparentVcs.MERGE_CONFLICT, null );
      }
    }
  }

  /**
   * Store the name of the changelist (aka activity if the UCM mode is on) since
   * the object behind it might be removed in the ChangeListManager during the
   * checkin process. 
   */
  private void storeChangeListName( List<Change> changes )
  {
    if( changes.size() > 0 )
    {
      ChangeListManager mgr = ChangeListManager.getInstance( project );
      LocalChangeList change = mgr.getChangeList( changes.get( 0 ) );
      submittedChangeListName = change.getName();
    }
  }

  private void adjustChangesWithRenamedParentFolders( List<Change> changes )
  {
    Set<VirtualFile> renamedFolders = getNecessaryRenamedFoldersForList( changes );
    if( renamedFolders.size() > 0 )
    {
      for( VirtualFile folder : renamedFolders )
        changes.add( ChangeListManager.getInstance( project ).getChange( folder ) );
    }
  }

  @SuppressWarnings({"ConstantConditions"})
  private void commitRenamedFolders( List<Change> changes, String comment, List<VcsException> errors )
  {
    for (Change change : changes)
    {
      if( VcsUtil.isRenameChange( change ) && VcsUtil.isChangeForFolder( change ) )
      {
        FilePath newFile = change.getAfterRevision().getFile();
        FilePath oldFile = change.getBeforeRevision().getFile();

        if( oldFile.getVirtualFileParent().getPath().equals( newFile.getVirtualFileParent().getPath() ))
        {
          host.renameAndCheckInFile( oldFile.getIOFile(), newFile.getName(), comment, errors );
        }
        else
        {
          host.moveRenameAndCheckInFile( oldFile.getPath(), newFile.getVirtualFileParent().getPath(), newFile.getName(), comment, errors );
        }
        host.renamedFolders.remove( newFile.getPath() );
        incrementProgress( newFile.getPath() );
      }
    }
  }

  private void commitNew( List<Change> changes, String comment,
                          HashSet<FilePath> processedFiles, List<VcsException> errors )
  {
    HashSet<FilePath> files = new HashSet<FilePath>();
    HashSet<FilePath> folders = new HashSet<FilePath>();
    HashSet<FilePath> checkedOutFolders = new HashSet<FilePath>();

    collectNewFilesAndFolders( changes, processedFiles, folders, files );

    //  Add all folders first, then add all files into these folders:
    //  - Checkout parent folders for all folders which are to be added.
    //  - Checkout parent folders for all files which are to be added.
    //  - Add new folders.
    //  - Add files into these folders in a batch mode - several files
    //    per command.
    //  - Checkin all parent folders for files and folders.

    addFoldersAndCheckoutParents( folders, checkedOutFolders, comment, errors );
    checkoutParentFoldersForFiles( files, checkedOutFolders, comment, errors );
    addFiles( files, comment, errors );
    setActivitiesForFiles( files, errors );
    checkinParentFolders( checkedOutFolders, comment, errors );
  }

  private void collectNewFilesAndFolders( List<Change> changes, HashSet<FilePath> processedFiles,
                                          HashSet<FilePath> folders, HashSet<FilePath> files )
  {
    for( Change change : changes )
    {
      if( VcsUtil.isChangeForNew( change ) )
      {
        FilePath filePath = change.getAfterRevision().getFile();
        if( filePath.isDirectory() )
          folders.add( filePath );
        else
        {
          files.add( filePath );
          analyzeParent( filePath, folders );
        }
      }
    }
    processedFiles.addAll( folders );
    processedFiles.addAll( files );
  }

  private void addFoldersAndCheckoutParents( HashSet<FilePath> folders, HashSet<FilePath> checkedOutFolders,
                                             String comment, List<VcsException> errors )
  {
    FilePath[] foldersSorted = folders.toArray( new FilePath[ folders.size() ] );
    foldersSorted = VcsUtil.sortPathsFromOutermost( foldersSorted );
    initProgress( foldersSorted.length );

    for( FilePath folder : foldersSorted )
    {
      FilePath parentFolder = folder.getParentPath();
      try
      {
        host.checkoutFile( parentFolder.getIOFile(), false, comment, true );
        checkedOutFolders.add( parentFolder );
        incrementProgress( CHECKOUT_FOLDER + parentFolder.getName() );
      }
      catch( VcsException e ) {  errors.add( e );  }

      host.addFileToCheckedoutFolder( folder.getIOFile(), comment, errors );
      host.deleteNewFile( folder.getVirtualFile() );
    }
  }

  private void checkoutParentFoldersForFiles( HashSet<FilePath> files, HashSet<FilePath> checkedOutFolders,
                                              String comment, List<VcsException> errors )
  {
    //  Collect and checkout parent folders for all files to be added
    for( FilePath file : files )
    {
      FilePath folder = file.getParentPath();
      if( !checkedOutFolders.contains( folder ) )
      {
        try
        {
          host.checkoutFile( folder.getIOFile(), false, comment, true );
          checkedOutFolders.add( folder );
        }
        catch( VcsException e ) {  errors.add( e );  }
      }
    }
  }

  private void addFiles( HashSet<FilePath> files, String comment, List<VcsException> errors )
  {
    initProgress( files.size() );
    for( FilePath file : files )
    {
      host.addFileToCheckedoutFolder( file.getIOFile(), comment, errors );
      host.deleteNewFile( file.getVirtualFile() );

      //-----------------------------------------------------------------------
      String showString = file.getName();
      if( file.getVirtualFileParent() != null )
        showString = file.getVirtualFileParent().getName() + "/" + file.getName();

      incrementProgress( ADDING_FILES + showString );
    }
  }

  /**
    If the file was checked out using one view's activity but is then
    moved to another changelist (activity) we must issue "chactivity"
    command for the file element so that subsequent "checkin" command
    behaves as desired.
   */
  private void setActivitiesForFiles( HashSet<FilePath> files, List<VcsException> errors )
  {
    if( host.getConfig().useUcmModel )
    {
      CCaseViewsManager viewsManager = CCaseViewsManager.getInstance( project );

      initProgress( files.size() );

      for( FilePath file : files )
      {
        String activity = viewsManager.getActivityOfViewOfFile( file );
        String currentActivity = getChangeListName( file );
        if(( activity != null ) && !activity.equals( currentActivity ) )
        {
          host.changeActivityForLastVersion( file, activity, currentActivity, errors );
        }
        incrementProgress( CHANGE_ACTIVITY + file.getName() );
      }
    }
  }

  private void checkinParentFolders( HashSet<FilePath> folders, String comment, List<VcsException> errors )
  {
    initProgress( folders.size() );
    for( FilePath folder : folders )
    {
      host.checkinFile( folder, comment, errors );
      incrementProgress( CHECKIN_FOLDER + folder.getName() );
    }
  }

  /**
   * If the parent of the file has status New or Unversioned - add it
   * to the list of folders OBLIGATORY for addition into the repository -
   * no file can be added into VSS without all higher folders are already
   * presented there.
   * Process with the parent's parent recursively.
   */
  private void analyzeParent( FilePath file, HashSet<FilePath> folders )
  {
    VirtualFile parent = file.getVirtualFileParent();
    FileStatus status = FileStatusManager.getInstance( project ).getStatus( parent );
    if( status == FileStatus.ADDED || status == FileStatus.UNKNOWN )
    {
      FilePath parentPath = file.getParentPath();
      folders.add( parentPath );
      analyzeParent( parentPath, folders );
    }
  }

  private void commitDeleted( List<Change> changes, String comment, List<VcsException> errors )
  {
    for( Change change : changes )
    {
      if( VcsUtil.isChangeForDeleted( change ) )
      {
        final FilePath fp = change.getBeforeRevision().getFile();
        host.removeFile( fp.getIOFile(), comment, errors );

        String path = VcsUtil.getCanonicalLocalPath( fp.getPath() );
        host.deletedFiles.remove( path );
        host.deletedFolders.remove( path );

        incrementProgress( fp.getPath() );
        ApplicationManager.getApplication().invokeLater( new Runnable() {
          public void run() { VcsDirtyScopeManager.getInstance( project ).fileDirty( fp );  }
        });
      }
    }
  }

  private void commitChanged( List<Change> changes, String comment,
                              HashSet<FilePath> processedFiles, List<VcsException> errors )
  {
    for( Change change : changes )
    {
      if( !VcsUtil.isChangeForNew( change ) &&
          !VcsUtil.isChangeForDeleted( change ) &&
          !VcsUtil.isChangeForFolder( change ) )
      {
        FilePath file = change.getAfterRevision().getFile();
        String newPath = file.getPath();
        String oldPath = host.renamedFiles.get( newPath );
        if( oldPath != null )
        {
          FilePath oldFile = change.getBeforeRevision().getFile();

          //  If parent folders' names of the revisions coinside, then we
          //  deal with the simple rename, otherwise we process full-scaled
          //  file movement across folders (packages).

          if( oldFile.getVirtualFileParent().getPath().equals( file.getVirtualFileParent().getPath() ))
          {
            host.renameAndCheckInFile( oldFile.getIOFile(), file.getName(), comment, errors );
          }
          else
          {
            String newFolder = file.getVirtualFileParent().getPath();
            host.moveRenameAndCheckInFile( oldPath, newFolder, file.getName(), comment, errors );
          }
          host.renamedFiles.remove( newPath );
        }
        else
        {
          host.checkinFile( file, comment, errors );

          CCaseViewsManager viewsManager = CCaseViewsManager.getInstance( project );
          if( host.getConfig().useUcmModel && viewsManager.isUcmViewForFile( file ) )
          {
            //  If the file was checked out using one view's activity but has then
            //  been moved to another changelist (activity) we must issue "chactivity"
            //  command for the file element so that subsequent "checkin" command
            //  behaves as desired.

            String activity = viewsManager.getCheckoutActivityForFile( file.getPath() );
            if(( activity != null ) && !activity.equals( submittedChangeListName ) )
            {
              TransparentVcs.LOG.info( " --changeActivityForLastVersion - activities do not coinside: [" +
                                       activity + "] vs [" + submittedChangeListName + "]" );
              host.changeActivityForLastVersion( file, activity, submittedChangeListName, errors );
            }
          }
        }

        processedFiles.add( file );
        incrementProgress( file.getPath() );
      }
    }
  }

  /**
   * Commit local deletion of files and folders to VCS.
   */
  public List<VcsException> scheduleMissingFileForDeletion( List<FilePath> paths )
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    for( FilePath file : paths )
    {
      String path = VcsUtil.getCanonicalLocalPath( file.getPath() );
      if( host.removedFiles.contains( path ) || host.removedFolders.contains( path ) )
      {
        host.removeFile( file.getIOFile(), null, errors );
      }

      host.removedFiles.remove( path );
      host.removedFolders.remove( path );
    }
    return errors;
  }

  public List<VcsException> scheduleUnversionedFilesForAddition( List<VirtualFile> files )
  {
    for( VirtualFile file : files )
    {
      host.add2NewFile( file );
      VcsUtil.markFileAsDirty( project, file );

      //  Extend status change to all parent folders if they are not
      //  included into the context of the menu action.
      extendStatus( file );
    }
    // Keep intentionally empty.
    return new ArrayList<VcsException>();
  }

  private void extendStatus( VirtualFile file )
  {
    FileStatusManager mgr = FileStatusManager.getInstance( project );
    VirtualFile parent = file.getParent();

    if( mgr.getStatus( parent ) == FileStatus.UNKNOWN )
    {
      host.add2NewFile( parent );
      VcsUtil.markFileAsDirty( project, parent );

      extendStatus( parent );
    }
  }
  
  private Set<VirtualFile> getNecessaryRenamedFoldersForList( List<Change> changes )
  {
    Set<VirtualFile> set = new HashSet<VirtualFile>();
    for( Change change : changes )
    {
      ContentRevision rev = change.getAfterRevision();
      if( rev != null )
      {
        for( String newFolderName : host.renamedFolders.keySet() )
        {
          if( rev.getFile().getPath().startsWith( newFolderName ) )
          {
            VirtualFile parent = VcsUtil.getVirtualFile( newFolderName );
            set.add( parent );
          }
        }
      }
    }
    for( Change change : changes )
    {
      ContentRevision rev = change.getAfterRevision();
      if( rev != null )
      {
        VirtualFile submittedParent = rev.getFile().getVirtualFile();
        if( submittedParent != null )
          set.remove( submittedParent );
      }
    }

    return set;
  }

  @Nullable
  private String getChangeListName( @NotNull FilePath file )
  {
    String changeListName = null;

    ChangeListManager mgr = ChangeListManager.getInstance( project );
    Change change = mgr.getChange( file );
    if( change != null )
    {
      changeListName = mgr.getChangeList( change ).getName();
    }

    return changeListName;
  }
  
  private void initProgress( int total )
  {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if( progress != null )
    {
      fraction = 1.0 / (double) total;
      progress.setIndeterminate( false );
      progress.setFraction( 0.0 );
    }
  }

  private void incrementProgress( String text ) throws ProcessCanceledException
  {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if( progress != null )
    {
      double newFraction = progress.getFraction();
      newFraction += fraction;
      progress.setFraction( newFraction );
      progress.setText( text );

      if( progress.isCanceled() )
        throw new ProcessCanceledException();
    }
  }
}
