/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;
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
  @NonNls private static final String CHECKIN_TITLE = "Checkin";
  @NonNls private static final String SCR_TITLE = "SCR Number";
  @NonNls private static final String FILE_NOT_IN_VOB_SIG = "element name not found";

  @NonNls private static final String UPDATE_SUCC_PREFIX_1 = "Processing dir";
  @NonNls private static final String UPDATE_SUCC_PREFIX_2 = "Loading ";
  @NonNls private static final String UPDATE_SUCC_PREFIX_3 = "End dir";
  @NonNls private static final String UPDATE_SUCC_PREFIX_4 = "Done loading";
  @NonNls private static final String UPDATE_SUCC_PREFIX_5 = "Log has been written"; 

  private Project project;
  private TransparentVcs host;

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
      String fileComment = cc.getCheckoutComment( new File( path.getPresentableUrl() ) );
      if( StringUtil.isNotEmpty( fileComment ) )
        commentsPerFile.add( fileComment );
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


  public boolean showCheckinDialogInAnyCase()   {  return false;  }
  public String  prepareCheckinMessage(String text)  {  return text;  }
  public String  getHelpId() {  return null;   }
  public String  getCheckinOperationName() {  return CHECKIN_TITLE;  }

  public String getRollbackOperationName() {
    return VcsBundle.message("changes.action.rollback.text");
  }
  
  public List<VcsException> commit( List<Change> changes, String comment )
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    HashSet<FilePath> processedFiles = new HashSet<FilePath>();

    clearTemporaryStatuses( changes );

    adjustChangesWithRenamedParentFolders( changes );

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

    commitNew( changes, comment, processedFiles, errors );
    commitDeleted( changes, comment, errors );
    commitChanged( changes, comment, processedFiles, errors );

    VcsUtil.refreshFiles( project, processedFiles );

    return errors;
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

  private void adjustChangesWithRenamedParentFolders( List<Change> changes )
  {
    Set<VirtualFile> renamedFolders = getNecessaryRenamedFoldersForList( changes );
    if( renamedFolders.size() > 0 )
    {
      for( VirtualFile folder : renamedFolders )
        changes.add( ChangeListManager.getInstance( project ).getChange( folder ) );
    }
  }

  private void commitRenamedFolders( List<Change> changes, String comment, List<VcsException> errors )
  {
    for (Change change : changes)
    {
      if( VcsUtil.isRenameChange( change ) && VcsUtil.isChangeForFolder( change ) )
      {
        FilePath newFile = change.getAfterRevision().getFile();
        FilePath oldFile = change.getBeforeRevision().getFile();

        host.renameAndCheckInFile( oldFile.getIOFile(), newFile.getName(), comment, errors );
        host.renamedFolders.remove( newFile.getPath() );
      }
    }
  }

  private void commitNew( List<Change> changes, String comment,
                          HashSet<FilePath> processedFiles, List<VcsException> errors )
  {
    HashSet<FilePath> folders = new HashSet<FilePath>();
    HashSet<FilePath> files = new HashSet<FilePath>();

    collectNewFilesAndFolders( changes, processedFiles, folders, files );
    commitFoldersAndFiles( folders, files, comment, errors );
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

  /**
   *  Add all folders first, then add all files into these folders.
   *  Difference between added and modified files is that added file
   *  has no "before" revision.
   */
  private void commitFoldersAndFiles( HashSet<FilePath> folders, HashSet<FilePath> files,
                                      String comment, List<VcsException> errors )
  {
    FilePath[] foldersSorted = folders.toArray( new FilePath[ folders.size() ] );
    foldersSorted = VcsUtil.sortPathsFromOutermost( foldersSorted );

    for( FilePath folder : foldersSorted )
      host.addFile( folder.getVirtualFile(), comment, errors );

    for( FilePath file : files )
      host.addFile( file.getVirtualFile(), comment, errors );
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
          String prevPath = oldFile.getPath();

          //  If parent folders' names of the revisions coinside, then we
          //  deal with the simle rename, otherwise we process full-scaled
          //  file movement across folders (packages).

          if( oldFile.getVirtualFileParent().getPath().equals( file.getVirtualFileParent().getPath() ))
          {
            host.renameAndCheckInFile( oldFile.getIOFile(), file.getName(), comment, errors );
          }
          else
          {
            String newFolder = file.getVirtualFileParent().getPath();
            host.moveRenameAndCheckInFile( prevPath, newFolder, file.getName(), comment, errors );
          }
          host.renamedFiles.remove( newPath );
        }
        else
        {
          if( host.getConfig().useUcmModel )
          {
            //  If the file was checked out using one view's activity but is then
            //  moved to another changelist (activity) we must issue "chactivity"
            //  command for the file element so that subsequent "checkin" command
            //  behaves as desired.
            String activity = host.getCheckoutActivityForFile( file.getPath() );
            String currentActivity = getChangeListName( file.getVirtualFile() );
            if( activity != null && activity != currentActivity && currentActivity != "Default" ) 
            {
                host.changeActivityForFile( file, activity, currentActivity, errors );
            }
          }

          host.checkinFile( file, comment, errors );
        }

        processedFiles.add( file );
      }
    }
  }

  public List<VcsException> rollbackChanges( List<Change> changes )
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    HashSet<FilePath> processedFiles = new HashSet<FilePath>();

    rollbackRenamedFolders( changes, processedFiles );
    rollbackNew( changes, processedFiles );
    rollbackDeleted( changes, processedFiles, errors );
    rollbackChanged( changes, processedFiles, errors );

    VcsUtil.refreshFiles( project, processedFiles );

    return errors;
  }

  private void rollbackRenamedFolders( List<Change> changes, HashSet<FilePath> processedFiles )
  {
    for( Change change : changes )
    {
      if( VcsUtil.isRenameChange( change ) && VcsUtil.isChangeForFolder( change ) )
      {
        //  The only thing which we can perform on this step is physical
        //  rename of the folder back to its former name, since we can't
        //  keep track of what consequent changes were done (due to Java
        //  semantics of the package rename).
        FilePath folder = change.getAfterRevision().getFile();
        File folderNew = folder.getIOFile();
        File folderOld = change.getBeforeRevision().getFile().getIOFile();
        folderNew.renameTo( folderOld );
        host.renamedFolders.remove( VcsUtil.getCanonicalLocalPath( folderNew.getPath() ) );
        processedFiles.add( folder );
      }
    }
  }

  private void rollbackNew( List<Change> changes, HashSet<FilePath> processedFiles )
  {
    HashSet<FilePath> filesAndFolder = new HashSet<FilePath>();
    collectNewChangesBack( changes, filesAndFolder, processedFiles );

    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    for( FilePath file : filesAndFolder )
    {
      host.deleteNewFile( file.getPath() );
      mgr.fileDirty( file );
    }
  }

  /**
   * For each accumulated (to be rolledback) folder - collect ALL files
   * in the change lists with the status NEW (ADDED) which are UNDER this folder.
   * This ensures that no file will be left in any change list with status NEW.
   */
  private void collectNewChangesBack( List<Change> changes, HashSet<FilePath> newFilesAndfolders,
                                      HashSet<FilePath> processedFiles )
  {
    HashSet<FilePath> foldersNew = new HashSet<FilePath>();
    for( Change change : changes )
    {
      if( VcsUtil.isChangeForNew( change ) )
      {
        FilePath filePath = change.getAfterRevision().getFile();
        if( !filePath.isDirectory() )
          newFilesAndfolders.add( filePath );
        else
          foldersNew.add( filePath );
        processedFiles.add( filePath );
      }
    }

    ChangeListManager clMgr = ChangeListManager.getInstance(project);
    FileStatusManager fsMgr = FileStatusManager.getInstance(project);
    List<VirtualFile> allAffectedFiles = clMgr.getAffectedFiles();

    for( VirtualFile file : allAffectedFiles )
    {
      FileStatus status = fsMgr.getStatus( file );
      if( status == FileStatus.ADDED )
      {
        for( FilePath folder : foldersNew )
        {
          if( file.getPath().toLowerCase().startsWith( folder.getPath().toLowerCase() ))
          {
            FilePath path = clMgr.getChange( file ).getAfterRevision().getFile();
            newFilesAndfolders.add( path );
          }
        }
      }
    }
    newFilesAndfolders.addAll( foldersNew );
  }

  private void rollbackDeleted( List<Change> changes, HashSet<FilePath> processedFiles, List<VcsException> errors )
  {
    for( Change change : changes )
    {
      if( VcsUtil.isChangeForDeleted( change ))
      {
        FilePath filePath = change.getBeforeRevision().getFile();
        rollbackMissingFileDeletion( filePath, errors );
        processedFiles.add( filePath );
      }
    }
  }

  private void rollbackChanged( List<Change> changes, HashSet<FilePath> processedFiles, List<VcsException> errors )
  {
    for( Change change : changes )
    {
      if( !VcsUtil.isChangeForNew( change ) &&
          !VcsUtil.isChangeForDeleted( change ) &&
          !VcsUtil.isChangeForFolder( change ) )
      {
        FilePath filePath = change.getAfterRevision().getFile();
        String path = filePath.getPath();

        if( VcsUtil.isRenameChange( change ) )
        {
          //  Track two different cases:
          //  - we delete the file which is already in the repository.
          //    Here we need to "Get" the latest version of the original
          //    file from the repository and delete the new file.
          //  - we delete the renamed file which is new and does not exist
          //    in the repository. We need to ignore the error message from
          //    the SourceSafe ("file not existing") and just delete the
          //    new file.

          List<VcsException> localErrors = new ArrayList<VcsException>();
          FilePath oldFile = change.getBeforeRevision().getFile();
          host.undoCheckoutFile( oldFile.getIOFile(), localErrors );
          if( localErrors.size() > 0 && !isUnknownFileError( localErrors ) )
            errors.addAll( localErrors );

          host.renamedFiles.remove( filePath.getPath() );
          FileUtil.delete( new File( path ) );
        }
        else
        {
          host.undoCheckoutFile( filePath.getVirtualFile(), errors );
        }
        processedFiles.add( filePath );
      }
    }
  }

  /**
   * Commit local deletion of files and folders to VCS.
   */
  public List<VcsException> scheduleMissingFileForDeletion( List<FilePath> paths )
  {
    List<File> files = ChangesUtil.filePathsToFiles( paths );
    List<VcsException> errors = new ArrayList<VcsException>();
    for( File file : files )
    {
      String path = VcsUtil.getCanonicalPath( file );
      if( host.removedFiles.contains( path ) || host.removedFolders.contains( path ) )
      {
        host.removeFile( file, null, errors );
      }

      host.removedFiles.remove( path );
      host.removedFolders.remove( path );
    }
    return errors;
  }

  public List<VcsException> rollbackMissingFileDeletion( List<FilePath> paths )
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    for( FilePath path : paths )
    {
      rollbackMissingFileDeletion( path, errors );
    }
    return errors;
  }

  private void rollbackMissingFileDeletion( FilePath path, List<VcsException> errors )
  {
    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance( project );

    String normPath = VcsUtil.getCanonicalPath( path.getIOFile() );
    if( host.isFolderRemoved( normPath ) )
    {
      //  For ClearCase to get back the locally removed folder, it is
      //  necessary to issue "Update" command. This will revert it to the
      //  state before the checking out on deletion.
      updateFile( path.getPath(), errors );
    }
    else
    {
      //  For ClearCase to get back the locally removed file:
      //  1. Issue "Undo Checkout" command. This will revert it to the state
      //     before its checkout on deletion (if it was checked out previously).
      //  2. Otherwise (we rollback the file which was not previusly checked
      //     out) perform "Update".
      List<VcsException> localErrors = new ArrayList<VcsException>();
      host.undoCheckoutFile( path.getIOFile(), localErrors );
      if( localErrors.size() > 0 )
      {
        updateFile( path.getPath(), errors );
      }
    }
    mgr.fileDirty( path );
  }

  public List<VcsException> scheduleUnversionedFilesForAddition( List<VirtualFile> files )
  {
    for( VirtualFile file : files )
    {
      host.add2NewFile( file.getPath() );
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
  
  public List<VcsException> rollbackModifiedWithoutCheckout(final List<VirtualFile> files)
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    for( VirtualFile file : files )
    {
      updateFile( file.getPath(), errors );
      file.refresh( true, true );
    }
    return errors;
  }

  private static void updateFile( String path, List<VcsException> errors )
  {
    try
    {
      String err = TransparentVcs.cleartoolWithOutput( "update", "-overwrite", path );
      if( err != null )
      {
        String[] lines = LineTokenizer.tokenize( err, false );
        for( String line : lines )
        {
          if( !lineStartsWithKnownPrefix( line ) )
          {
            VcsException e = new VcsException( line );
            e.setIsWarning( true );
            errors.add( e );
          }
        }
      }
    }
    catch( ClearCaseException e ) {  errors.add( new VcsException( e ) );  }
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

  private static boolean isUnknownFileError( List<VcsException> errors )
  {
    for( VcsException exc : errors )
    {
      if( exc.getMessage().toLowerCase().indexOf( FILE_NOT_IN_VOB_SIG ) != -1 )
        return true;
    }
    return false;
  }

  private static boolean lineStartsWithKnownPrefix( String line )
  {
    return  line.startsWith( UPDATE_SUCC_PREFIX_1 ) || line.startsWith( UPDATE_SUCC_PREFIX_2 ) ||
            line.startsWith( UPDATE_SUCC_PREFIX_3 ) || line.startsWith( UPDATE_SUCC_PREFIX_4 ) ||
            line.startsWith( UPDATE_SUCC_PREFIX_5 );
  }

  @Nullable
  private String getChangeListName( VirtualFile file )
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
}