/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package net.sourceforge.transparent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
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

  private Project myProject;
  private TransparentVcs host;

  public CCaseCheckinEnvironment( Project project, TransparentVcs host )
  {
    myProject = project;
    this.host = host;
  }

  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinProject( Refreshable panel )
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
    TransparentConfiguration config = TransparentConfiguration.getInstance( myProject );
    ClearCase cc = host.getClearCase();
    HashSet<String> commentsPerFile = new HashSet<String>();
    for( FilePath path : filesToCheckin )
    {
      String fileComment = cc.getCheckoutComment( new File( path.getPresentableUrl() ) );
      if( fileComment != null && fileComment.length() > 0 )
        commentsPerFile.add( fileComment );
    }

    StringBuilder overallComment = null;
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

  public List<VcsException> commit(List<Change> changes, String preparedComment)
  {
    List<VcsException> errors = new ArrayList<VcsException>();
    HashSet<FilePath> processedFiles = new HashSet<FilePath>();

    try
    {
      commitNew( changes, processedFiles );
      commitChanged( changes, processedFiles );
    }
    catch( VcsException e )
    {
      errors.add( e );
    }

    for( FilePath path : processedFiles )
    {
      VcsUtil.markFileAsDirty(myProject, path);
    }
    return errors;
  }

  /**
   *  Add all folders first, then add all files into these folders.
   *  Difference between added and modified files is that added file
   *  has no "before" revision.
   */
  private void commitNew( List<Change> changes, HashSet<FilePath> processedFiles ) throws VcsException
  {
    ArrayList<FilePath> folders = new ArrayList<FilePath>();
    ArrayList<FilePath> files = new ArrayList<FilePath>();
    for( Change change : changes )
    {
      if( VcsUtil.isChangeForNew( change ) )
      {
        FilePath filePath = change.getAfterRevision().getFile();
        if( filePath.isDirectory() )
          folders.add( filePath );
        else
          files.add( filePath );
        processedFiles.add( filePath );
      }
    }

    FilePath[] foldersSorted = folders.toArray( new FilePath[ folders.size() ] );
    foldersSorted = VcsUtil.sortPathsFromOutermost( foldersSorted );
    for( FilePath folder : foldersSorted )
      host.addDirectory( folder.getVirtualFileParent().getPath(), folder.getName(), null );

    for( FilePath file : files )
      host.addFile( file.getVirtualFileParent().getPath(), file.getName(), null );
  }

  private void commitChanged( List<Change> changes, HashSet<FilePath> processedFiles ) throws VcsException
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
            host.renameAndCheckInFile( prevPath, file.getName(), null );
          }
          else
          {
            String newFolder = file.getVirtualFileParent().getPath();
            host.moveRenameAndCheckInFile( prevPath, newFolder, file.getName(), null );
          }
          host.renamedFiles.remove( newPath );
        }
        else
        {
          host.checkinFile( file.getPath(), null );
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

    for( FilePath path : processedFiles )
      VcsUtil.markFileAsDirty( myProject, path );

    return errors;
  }

  private static void rollbackNew( List<Change> changes, HashSet<FilePath> processedFiles )
  {
    ArrayList<FilePath> folders = new ArrayList<FilePath>();
    ArrayList<FilePath> files = new ArrayList<FilePath>();
    for( Change change : changes )
    {
      if( VcsUtil.isChangeForNew( change ) )
      {
        FilePath filePath = change.getAfterRevision().getFile();
        if( filePath.isDirectory() )
          folders.add( filePath );
        else
          files.add( filePath );
        processedFiles.add( filePath );
      }
    }

    for( FilePath file : files )
      new File( file.getPath() ).delete();

    //  Sort folders in descending order - from the most inner folder
    //  to the outmost one.
    FilePath[] foldersSorted = folders.toArray( new FilePath[ folders.size() ] );
    foldersSorted = VcsUtil.sortPathsFromInnermost( foldersSorted );
    for( FilePath folder : foldersSorted )
      FileUtil.delete( new File( folder.getPath() ) );
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
          updateFile( oldFile.getPath(), localErrors );
          if( !isUnknownFileError( localErrors ) )
            errors.addAll( localErrors );

          host.renamedFiles.remove( filePath.getPath() );
          FileUtil.delete( new File( path ) );
        }
        else
        {
          undoCheckout( path, errors );
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
      String path = file.getPath();
      try
      {
        if( host.removedFiles.contains( path ) )
        {
          host.removeFile( path, null );
        }
        else
        {
          host.removeDirectory( path, null );
        }
      }
      catch( VcsException e )
      {
        errors.add( e );
      }

      host.removedFiles.remove( path );
      host.removedFolders.remove( path );
    }
    return errors;
  }

  public List<VcsException> rollbackMissingFileDeletion( List<FilePath> paths )
  {
    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance( myProject );
    List<VcsException> errors = new ArrayList<VcsException>();

    for( FilePath filePath : paths )
    {
      File file = filePath.getIOFile();
      updateFile( file.getPath(), errors );
      mgr.fileDirty( filePath );
    }
    return errors;
  }

  public List<VcsException> scheduleUnversionedFilesForAddition( List<VirtualFile> files )
  {
    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance( myProject );

    for( VirtualFile file : files )
    {
      host.add2NewFile( file.getPath() );
      mgr.fileDirty( file );
      file.refresh( true, true );
    }
    // Keep intentionally empty.
    return new ArrayList<VcsException>();
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

  private void undoCheckout( String path, List<VcsException> errors )
  {
    try {  host.undoCheckoutFile( path );  }
    catch( VcsException e ) {  errors.add( new VcsException( e ) );  }
  }

  private static boolean isUnknownFileError( List<VcsException> errors )
  {
    return false;
  }
}