/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import static net.sourceforge.transparent.TransparentVcs.SUCCESSFUL_CHECKOUT;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Dec 6, 2006
 */
public class CCaseChangeProvider implements ChangeProvider
{
  @NonNls private final static String COLLECT_MSG = "Collecting Writables";
  @NonNls private final static String SEARCHNEW_MSG = "Searching New";
  @NonNls private final static String FAIL_2_CONNECT_MSG = "Failed to connect to ClearCase Server: ";
  @NonNls private final static String FAIL_2_CONNECT_TITLE = "Server Connection Problem";

  //  Sometimes we need to explicitely distinguish between different
  //  ClearCase implementations since for one of them we use optimized
  //  scheme for file statuses determination. Part of class name is the
  //  best way for that.
  @NonNls private final static String COMMAND_LINE_CLASS_SIG = "Line";

  private static final Logger LOG = Logger.getInstance("#net.sourceforge.transparent.CCaseChangeProvider");

  private Project project;
  private TransparentVcs host;
  private ProgressIndicator progress;
  private boolean isBatchUpdate;

  private HashSet<String> filesNew = new HashSet<String>();
  private HashSet<String> filesChanged = new HashSet<String>();
  private HashSet<String> filesHijacked = new HashSet<String>();
  private HashSet<String> filesIgnored = new HashSet<String>();

  public CCaseChangeProvider( Project project, TransparentVcs host )
  {
    this.project = project;
    this.host = host;
  }

  public boolean isModifiedDocumentTrackingRequired() { return false;  }

  public void getChanges( final VcsDirtyScope dirtyScope, final ChangelistBuilder builder,
                          final ProgressIndicator progressIndicator )
  {
    LOG.info( "-- ChangeProvider -- ");
    LOG.info( "   Dirty files: " + dirtyScope.getDirtyFiles().size() +
              ", dirty recursive directories: " + dirtyScope.getRecursivelyDirtyDirectories().size() );

    progress = progressIndicator;
    isBatchUpdate = (dirtyScope.getRecursivelyDirtyDirectories().size() > 0);
    initInternals();

    try
    {
      if( isBatchUpdate )
      {
        iterateOverProjectStructure( dirtyScope );
      }
      iterateOverDirtyDirectories( dirtyScope );
      iterateOverDirtyFiles( dirtyScope );

      /**
       * Transform data accumulated in the internal data structures (filesNew,
       * filesChanged, filesDeleted, host.renamedFiles) into "Change" format
       * acceptable by ChangelistBuilder.
      */
      addNewOrRenamedFiles( builder );
      addChangedFiles( builder );
      addRemovedFiles( builder );
      addIgnoredFiles( builder );
    }
    catch( ClearCaseException e )
    {
      @NonNls String message = FAIL_2_CONNECT_MSG + e.getMessage();
      if( TransparentVcs.isServerDownMessage( e.getMessage() ))
      {
        message += "\n\nSwitching to the isOffline mode";
        host.getConfig().isOffline = true;
      }
      final String msg = message;
      ApplicationManager.getApplication().invokeLater( new Runnable() { public void run() { VcsUtil.showErrorMessage( project, msg, FAIL_2_CONNECT_TITLE ); } });
    }
    finally
    {
      TransparentVcs.LOG.info( "-- EndChangeProvider| New(+renamed): " + filesNew.size() + ", modified: " + filesChanged.size() +
                               ", hijacked:" + filesHijacked.size() + ", ignored: " + filesIgnored.size() );
    }
  }

  /**
   *  Iterate over the project structure, find all writable files in the project,
   *  and check their status against the VSS repository. If file exists in the repository
   *  it is assigned "changed" status, otherwise it has "new" status.
   */
  private void iterateOverProjectStructure( final VcsDirtyScope dirtyScope )
  {
    for( FilePath path : dirtyScope.getRecursivelyDirtyDirectories() )
      iterateOverProjectPath( path );
  }

  /**
   *  Deleted and New folders are marked as dirty too and we provide here
   *  special processing for them.
   */
  private void iterateOverDirtyDirectories( final VcsDirtyScope dirtyScope )
  {
    for( FilePath path : dirtyScope.getDirtyFiles() )
    {
      String fileName = path.getPath();
      VirtualFile file = VcsUtil.getVirtualFile( fileName );

      if( file != null && path.isDirectory() && host.fileIsUnderVcs( file ) )
      {
        if( host.isFileIgnored( file ))
          filesIgnored.add( path.getPath() );
        else
        //  Check that folder physically exists.
        if( !host.fileExistsInVcs( path ))
          filesNew.add( path.getPath() );
      }
    }
  }

  private void iterateOverDirtyFiles( final VcsDirtyScope scope )
  {
    List<String> writableFiles = new ArrayList<String>();
    for( FilePath path : scope.getDirtyFiles() )
    {
      String fileName = path.getPath();
      VirtualFile file = VcsUtil.getVirtualFile( fileName );

      if( host.isFileIgnored( file ))
        filesIgnored.add( fileName );
      else
      if( isFileCCaseProcessable( file ) && isProperNotification( path ) )
        writableFiles.add( fileName );
    }
    analyzeWritableFiles( writableFiles );
  }

  private void iterateOverProjectPath( FilePath path )
  {
    LOG.info( "-- ChangeProvider - Iterating over content root: " + path.getPath() );
    if( progress != null )
      progress.setText( COLLECT_MSG );

    List<String> writableFiles = collectWritableFiles( path );

    LOG.info( "-- ChangeProvider - Found: " + writableFiles.size() + " writable files." );
    if( progress != null )
      progress.setText( SEARCHNEW_MSG );
    
    analyzeWritableFiles( writableFiles );
  }

  private List<String> collectWritableFiles( final FilePath filePath )
  {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance( project ).getFileIndex();
    final List<String> writableFiles = new ArrayList<String>();

    VirtualFile vf = VcsUtil.getVirtualFile( filePath.getPath() );
    if( vf != null )
    {
      fileIndex.iterateContentUnderDirectory( vf, new ContentIterator()
        {
          public boolean processFile( VirtualFile file )
          {
            String path = file.getPath();
            if( VcsUtil.isPathUnderProject( project, path ) && isValidFile( file ) )
            {
              if( host.isFileIgnored( file ) )
                filesIgnored.add( path );
              else
                writableFiles.add( path );
            }
            return true;
          }
        } );
    }
    return writableFiles;
  }

  private void analyzeWritableFiles( List<String> writableFiles )
  {
    List<String> writableExplicitFiles = new ArrayList<String>();

    final List<String> newFiles = new ArrayList<String>();

    //  Exclude those files for which status is known apriori.
    selectExplicitWritableFiles( writableFiles, writableExplicitFiles );

    if( host.getClearCase().getName().indexOf( COMMAND_LINE_CLASS_SIG ) == -1 ||
        writableFiles.size() == 1 )
    {
      LOG.info( "ChangeProvider - Analyzing writable files on per-file basis" );
      for( String path : writableExplicitFiles )
      {
        LOG.info( "ChangeProvider - Issue \"ls\" command for getting information on writable file" );

        Status _status = host.getStatus( new File( path ) );
        if( _status == Status.NOT_AN_ELEMENT )
          newFiles.add( path );
        else
        if( _status == Status.CHECKED_OUT )
          filesChanged.add( path );
        else
        if( _status == Status.HIJACKED )
          filesHijacked.add( path );
      }
      LOG.info( "ChangeProvider - \"PROPERTIES\" command finished" );
    }
    else
    {
      LOG.info( "ChangeProvider - Analyzing writables in batch mode using CLEARTOOL on " + writableFiles.size() + " files." );

      StatusMultipleProcessor processor = new StatusMultipleProcessor( writableExplicitFiles );
      processor.execute();
      LOG.info( "ChangeProvider - CLEARTOOL LS batch command finished." );

      for( String path : writableFiles )
      {
        if( processor.isNonexist( path ))
          newFiles.add( path );
        else
        if( processor.isCheckedout( path ))
          filesChanged.add( path );
        else
        if( processor.isHijacked( path ))
          filesHijacked.add( path );
      }
    }

    if( isBatchUpdate )
    {
      //  For each new file check whether parent folders structure is also new.
      //  If so - mark these folders as dirty and assign them new statuses on the
      //  next iteration to "getChanges()".
      final List<String> newFolders = new ArrayList<String>();
      final HashSet<String> processedFolders = new HashSet<String>();
      for( String file : newFiles )
      {
        if( !isPathUnderProcessedFolders( processedFolders, file ))
          analyzeParentFoldersForPresence( file, newFolders, processedFolders );
      }

      filesNew.addAll( newFolders );
    }
    filesNew.addAll( newFiles );
  }

  /**
   * Do not analyze the file if we know that this file just has been
   * successfully checked out from the repository, its RO status is
   * writable and it is ready for editing.
   */
  private void selectExplicitWritableFiles( List<String> list, List<String> newWritables )
  {
    for( String path : list )
    {
      VirtualFile file = VcsUtil.getVirtualFile( path );

      Boolean isCheckoutResult = file.getUserData( SUCCESSFUL_CHECKOUT );
      if( isCheckoutResult != null && isCheckoutResult.booleanValue() )
      {
        //  Do not forget to delete this property right after the change
        //  is classified, otherwise this file will always be determined
        //  as modified.
        file.putUserData( SUCCESSFUL_CHECKOUT, null );
        filesChanged.add( file.getPath() );
      }
      else
      {
        newWritables.add( path );
      }
    }
  }

  //---------------------------------------------------------------------------
  //  For a given file which is known that it is new, check also its direct
  //  parent folder for presence in the VSS repository, and then all its indirect
  //  parent folders until we reach project boundaries.
  //---------------------------------------------------------------------------
  private void analyzeParentFoldersForPresence( String file, List<String> newFolders,
                                                HashSet<String> processed )
  {
    File parentIO = new File( file ).getParentFile();
    VirtualFile parentVF = VcsUtil.getVirtualFile( parentIO );
    String parentPath = parentVF.getPath();

    if( host.fileIsUnderVcs( parentVF ) && !processed.contains( parentPath ) )
    {
      LOG.info( "ChangeProvider - Check potentially new folder" );
      
      processed.add( parentPath.toLowerCase() );
      if( !host.fileExistsInVcs( parentVF ))
      {
        LOG.info( "                 Folder [" + parentVF.getName() + "] is not in the repository" );
        newFolders.add( parentPath );
        analyzeParentFoldersForPresence( parentPath, newFolders, processed );
      }
    }
  }

  private void addNewOrRenamedFiles( final ChangelistBuilder builder )
  {
    for( String path : filesNew )
    {
      FilePath newFP = VcsUtil.getFilePath( path );
      String   oldName = host.renamedFiles.get( path );
      if( host.containsNew( path ) )
      {
        builder.processChange( new Change( null, new CurrentContentRevision( newFP ) ));
      }
      else
      if( oldName == null )
      {
        VirtualFile vFile = VcsUtil.getVirtualFile( path );
        builder.processUnversionedFile( vFile );
      }
      else
      {
        ContentRevision before = new CurrentContentRevision( VcsUtil.getFilePath( oldName ) );
        builder.processChange( new Change( before, new CurrentContentRevision( newFP )));
      }
    }
  }

  private void addChangedFiles( final ChangelistBuilder builder )
  {
    for( String path : filesChanged )
    {
      final FilePath fp = VcsUtil.getFilePath( path );
      builder.processChange( new Change( new CCaseContentRevision( host, fp, project ), new CurrentContentRevision( fp )));
    }

    for( String path : filesHijacked )
    {
      final FilePath fp = VcsUtil.getFilePath( path );
      builder.processChange( new Change( new CCaseContentRevision( host, fp, project ), new CurrentContentRevision( fp ), FileStatus.HIJACKED ));
    }
  }

  private void addRemovedFiles( final ChangelistBuilder builder )
  {
    final HashSet<String> files = new HashSet<String>();
    files.addAll( host.removedFolders );
    files.addAll( host.removedFiles );

    for( String path : files )
      builder.processLocallyDeletedFile( VcsUtil.getFilePath( path ) );
  }

  private void addIgnoredFiles( final ChangelistBuilder builder )
  {
    for( String path : filesIgnored )
      builder.processIgnoredFile( VcsUtil.getVirtualFile( path ) );
  }

  private static boolean isPathUnderProcessedFolders( HashSet<String> folders, String path )
  {
    String parentPathToCheck = new File( path ).getParent().toLowerCase();
    for( String folderPath : folders )
    {
      if( parentPathToCheck == folderPath )
        return true;
    }
    return false;
  }

  /**
   * For the renamed or moved file we receive two change requests: one for
   * the old file and one for the new one. For renamed file old request differs
   * in filename, for the moved one - in parent path name. This request must be
   * ignored since all preliminary information is already accumulated.
   */
  private static boolean isProperNotification( final FilePath filePath )
  {
    String oldName = filePath.getName();
    String newName = (filePath.getVirtualFile() == null) ? "" : filePath.getVirtualFile().getName();
    String oldParent = (filePath.getVirtualFileParent() == null) ? "" : filePath.getVirtualFileParent().getPath();
    String newParent = filePath.getPath().substring( 0, filePath.getPath().length() - oldName.length() - 1 );

    //  Check the case when the file is deleted - its FilePath's VirtualFile
    //  component is null and thus new name is empty.
    return newParent.equals( oldParent ) &&
          ( newName.equals( oldName ) || (newName == "" && oldName != "") );
  }

  private void initInternals()
  {
    filesNew.clear();
    filesChanged.clear();
    filesHijacked.clear();
    filesIgnored.clear();
  }

  private boolean isFileCCaseProcessable( VirtualFile file )
  {
    return isValidFile( file ) && VcsUtil.isPathUnderProject( project, file.getPath() );
  }

  private static boolean isValidFile( VirtualFile file )
  {
    return (file != null) && file.isWritable() && !file.isDirectory();
  }
}
