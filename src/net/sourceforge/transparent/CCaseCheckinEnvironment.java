/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package net.sourceforge.transparent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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

    commitNew( changes, comment, processedFiles, errors );
    commitChanged( changes, comment, processedFiles, errors );

    VcsUtil.refreshFiles( project, processedFiles );

    return errors;
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
    FileStatusManager mgr = FileStatusManager.getInstance(project);
    if( mgr.getStatus( parent ) == FileStatus.ADDED ||
        mgr.getStatus( parent ) == FileStatus.UNKNOWN )
    {
      FilePath parentPath = file.getParentPath();
      folders.add( parentPath );
      analyzeParent( parentPath, folders );
    }
  }

  private void commitChanged( List<Change> changes, String comment,
                              HashSet<FilePath> processedFiles, List<VcsException> errors )
  {
    for( Change change : changes )
    {
      FilePath file = change.getAfterRevision().getFile();
      ContentRevision before = change.getBeforeRevision();

      if( !VcsUtil.isChangeForNew( change ) )
      {
        String newPath = file.getPath();
        String oldPath = host.renamedFiles.get( newPath );
        if( oldPath != null )
        {
          FilePath oldFile = before.getFile();
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

    rollbackNew( changes, processedFiles );
    rollbackChanged( changes, processedFiles, errors );

    VcsUtil.refreshFiles( project, processedFiles );

    return errors;
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
  private void rollbackChanged( List<Change> changes, HashSet<FilePath> processedFiles, List<VcsException> errors )
  {
    for( Change change : changes )
    {
      if( !VcsUtil.isChangeForNew( change ) )
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
    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    List<VcsException> errors = new ArrayList<VcsException>();

    for( FilePath path : paths )
    {
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
    return errors;
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
      String err = TransparentVcs.updateFile( path );
      if( err != null )
        errors.add( new VcsException( err ) );
    }
    catch( ClearCaseException e ) {  errors.add( new VcsException( e ) );  }
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
}