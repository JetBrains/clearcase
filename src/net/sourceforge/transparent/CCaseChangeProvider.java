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

  private static final Logger LOG = Logger.getInstance("#net.sourceforge.transparent.CCaseChangeProvider");

  private Project project;
  private TransparentVcs host;
  private ProgressIndicator progress;

  private HashSet<String> filesNew = new HashSet<String>();
  private HashSet<String> filesChanged = new HashSet<String>();
  private HashSet<String> filesHijacked = new HashSet<String>();
  private HashSet<String> filesIgnored = new HashSet<String>();
  private ArrayList<String> foldersAbcent = new ArrayList<String>();

  public CCaseChangeProvider( Project project, TransparentVcs host )
  {
    this.project = project;
    this.host = host;
  }

  public boolean isModifiedDocumentTrackingRequired() { return false;  }

  public void getChanges( final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress )
  {
    LOG.info( "-- ChangeProvider -- ");
    LOG.info( "   Dirty files: " + dirtyScope.getDirtyFiles().size() +
              ", dirty recursive directories: " + dirtyScope.getRecursivelyDirtyDirectories().size() );

    boolean isBatchUpdate = (dirtyScope.getRecursivelyDirtyDirectories().size() > 0);
    this.progress = progress;
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
      LOG.info( "-- EndChangeProvider -- ");
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
    {
      iterateOverProjectPath( path );
    }
  }

  private void iterateOverProjectPath( FilePath path )
  {
    LOG.info( "-- ChangeProvider - Iterating over project structure starting from scope root: " + path.getPath() );
    if( progress != null )
      progress.setText( COLLECT_MSG );

    List<String> writableFiles = new ArrayList<String>();
    collectSuspiciousFiles( path, writableFiles );
    LOG.info( "-- ChangeProvider - Found: " + writableFiles.size() + " writable files." );

    if( progress != null )
      progress.setText( SEARCHNEW_MSG );
    analyzeWritableFiles( writableFiles );
  }

  private void collectSuspiciousFiles( final FilePath filePath, final List<String> writableFiles )
  {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance( project ).getFileIndex();

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
  }

  private void analyzeWritableFiles( List<String> writableFiles )
  {
    final List<String> newFiles = new ArrayList<String>();
    final List<String> newFolders = new ArrayList<String>();

    if( host.getClearCase().getName().indexOf( "Line" ) == -1 ||
        writableFiles.size() == 1 )
    {
      LOG.info( "ChangeProvider - Analyzing writable files on per-file basis" );
      for( String path : writableFiles )
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

      StatusMultipleProcessor processor = new StatusMultipleProcessor( writableFiles );
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

    //  For each new file check whether some subfolders structure above it
    //  is also new.
    final List<String> processedFolders = new ArrayList<String>();
    for( String file : newFiles )
    {
      if( !isPathUnderAbsentFolders( file ))
        analyzeParentFolderStructureForPresence( file, newFolders, processedFolders );
    }

    filesNew.addAll( newFolders );
    filesNew.addAll( newFiles );
  }

  //---------------------------------------------------------------------------
  //  For a given file which is known that it is new, check also its direct
  //  parent folder for presence in the VSS repository, and then all its indirect
  //  parent folders until we reach project boundaries.
  //---------------------------------------------------------------------------
  private void  analyzeParentFolderStructureForPresence( String file, List<String> newFolders,
                                                         List<String> processedFolders )
  {
    VirtualFile parent = VcsUtil.getVirtualFile( new File( file ).getParentFile() );

    if( host.fileIsUnderVcs( parent ) && !processedFolders.contains( parent.getPath() ) )
    {
      LOG.info( "ChangeProvider - Check potentially new folder" );
      
      processedFolders.add( parent.getPath() );
      if( !host.fileExistsInVcs( parent ))
      {
        LOG.info( "ChangeProvider - Folder does not exist in the repository" );

        newFolders.add( parent.getPath() );
        foldersAbcent.add( parent.getPath() );
        analyzeParentFolderStructureForPresence( parent.getPath(), newFolders, processedFolders );
      }
    }
  }
  /**
   *  Deleted and New folders are marked as dirty too and we provide here
   *  special processing for them.
   */
  private void iterateOverDirtyDirectories( final VcsDirtyScope dirtyScope )
  {
    for( FilePath path : dirtyScope.getDirtyFiles() )
    {
      if( path.isDirectory() )
      {
        LOG.info( "  Found dirty directory in the list of dirty files: " + path.getPath() );
        iterateOverProjectPath( path );
      }
    }
  }

  private void iterateOverDirtyFiles( final VcsDirtyScope scope )
  {
    for( FilePath path : scope.getDirtyFiles() )
    {
      VirtualFile file = path.getVirtualFile();
      String fileName = path.getPath();

      if( host.isFileIgnored( file ))
      {
        filesIgnored.add( fileName );
      }
      else
      //-----------------------------------------------------------------------
      //  Do not process files which have RO status at all.
      //  Generally it means that all files which were got through some sort of
      //  "Get Latest Version" or "Update" are not processed at all, especially
      //  since there is no necessity in that. All other cases - modified and
      //  new files are processed as usual.
      //-----------------------------------------------------------------------
      if( isFileCCaseProcessable( file ) )
      {
        if( isProperNotification( path ) )
        {
          if( isPathUnderAbsentFolders( fileName ) )
          {
            filesNew.add( fileName );
          }
          else
          {
            Status status = host.getStatus( file );
            if( status != Status.NOT_AN_ELEMENT )
            {
              if( status == Status.HIJACKED )
                filesHijacked.add( fileName );
              else
                filesChanged.add( fileName );
            }
            else
              filesNew.add( fileName );
          }
        }
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
    final HashSet<String> folders = host.removedFolders;
    synchronized( folders )
    {
      for( String path : folders )
        builder.processLocallyDeletedFile( VcsUtil.getFilePath( path ) );
    }

    final HashSet<String> files = host.removedFiles;
    synchronized( files )
    {
      for( String path : files )
        builder.processLocallyDeletedFile( VcsUtil.getFilePath( path ) );
    }
  }

  private void addIgnoredFiles( final ChangelistBuilder builder )
  {
    for( String path : filesIgnored )
      builder.processIgnoredFile( VcsUtil.getVirtualFile( path ) );
  }

  private boolean isPathUnderAbsentFolders( String pathToCheck )
  {
    for( String path : foldersAbcent )
    {
      if( pathToCheck.startsWith( path ) )
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
    foldersAbcent.clear();
  }

  private boolean isFileCCaseProcessable( VirtualFile file )
  {
    return (file != null) && file.isWritable() && !file.isDirectory() &&
           VcsUtil.isPathUnderProject( project, file.getPath() ) &&
           !host.isFileIgnored( file );
  }

  private static boolean isValidFile( VirtualFile file )
  {
    return (file != null) && file.isWritable() && !file.isDirectory();
  }
}
