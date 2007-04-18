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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import static net.sourceforge.transparent.TransparentVcs.MERGE_CONFLICT;
import static net.sourceforge.transparent.TransparentVcs.SUCCESSFUL_CHECKOUT;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Dec 6, 2006
 */
public class CCaseChangeProvider implements ChangeProvider
{
  @NonNls private final static String REMINDER_TITLE = "Reminder";
  @NonNls private final static String REMINDER_TEXT = "Project started with ClearCase configured to be in the Offline mode.";

  @NonNls private final static String COLLECT_MSG = "Collecting Writables";
  @NonNls private final static String SEARCHNEW_MSG = "Searching New";
  @NonNls private final static String FAIL_2_CONNECT_MSG = "Failed to connect to ClearCase Server: ";
  @NonNls private final static String FAIL_2_CONNECT_TITLE = "Server Connection Problem";

  private static final Logger LOG = Logger.getInstance("#net.sourceforge.transparent.CCaseChangeProvider");

  private Project project;
  private TransparentVcs host;
  private ProgressIndicator progress;
  private boolean isBatchUpdate;
  private boolean isFirstShow;

  private HashSet<String> filesNew = new HashSet<String>();
  private HashSet<String> filesChanged = new HashSet<String>();
  private HashSet<String> filesHijacked = new HashSet<String>();
  private HashSet<String> filesIgnored = new HashSet<String>();
  private HashSet<String> filesMerge = new HashSet<String>();

  public CCaseChangeProvider( Project project, TransparentVcs host )
  {
    this.project = project;
    this.host = host;
    isFirstShow = true;
  }

  public boolean isModifiedDocumentTrackingRequired() { return false;  }

  public void getChanges( final VcsDirtyScope dirtyScope, final ChangelistBuilder builder,
                          final ProgressIndicator progressIndicator )
  {
    validateChangesOverTheHost( dirtyScope );
    LOG.info( "-- ChangeProvider -- ");
    LOG.info( "   Dirty files (" + dirtyScope.getDirtyFiles().size() + "): " + extMasks( dirtyScope.getDirtyFiles() ) +
              ", dirty recursive directories: " + dirtyScope.getRecursivelyDirtyDirectories().size() );

    progress = progressIndicator;
    isBatchUpdate = isBatchUpdate( dirtyScope );

    //  When we start for the very first time - show reminder that user possibly
    //  forgot that last time he set option to "Work offline".
    if( isBatchUpdate && isFirstShow && CCaseConfig.getInstance( project ).isOffline )
    {
      ApplicationManager.getApplication().invokeLater( new Runnable() {
         public void run() {  Messages.showWarningDialog( project, REMINDER_TEXT, REMINDER_TITLE );  }
       });
    }
    
    isFirstShow = false;
    initInternals();

    try
    {
      if( isBatchUpdate )
      {
        iterateOverProjectStructure( dirtyScope );
      }
      iterateOverDirtyDirectories( dirtyScope );
      iterateOverDirtyFiles( dirtyScope );

      //-----------------------------------------------------------------------
      //  Transform data accumulated in the internal data structures (filesNew,
      //  filesChanged, filesDeleted, host.renamedFiles) into "Change" format
      //  acceptable by ChangelistBuilder.
      //-----------------------------------------------------------------------
      addAddedFiles( builder );
      addChangedFiles( builder );
      addRemovedFiles( builder );
      addIgnoredFiles( builder );
      addMergeConflictFiles( builder );
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
      VirtualFile file = path.getVirtualFile();

      //  make sure that:
      //  - a file is a folder which exists physically
      //  - it is under out vcs and is not in the ignore list
      if( path.isDirectory() && (file != null) && host.fileIsUnderVcs( path ) )
      {
        if( host.isFileIgnored( file ))
          filesIgnored.add( fileName );
        else
        {
          String refName = discoverOldName( fileName );

          //  Check that folder physically exists.
          if( !host.fileExistsInVcs( refName ))
            filesNew.add( fileName );
          else
          //  NB: Do not put to the "Changed" list those folders which are under
          //      the renamed one since we will have troubles in checking such
          //      folders in (it is useless, BTW).
          //      Simultaneously, this prevents valid processing of renamed folders
          //      that are under another renamed folders.
          //  Todo Inner rename.
          if( !refName.equals( fileName ) && !isUnderRenamedFolder( fileName ) )
            filesChanged.add( fileName );
        }
      }
    }
  }

  private void iterateOverDirtyFiles( final VcsDirtyScope scope )
  {
    List<String> writableFiles = new ArrayList<String>();
    for( FilePath path : scope.getDirtyFiles() )
    {
      String fileName = path.getPath();
      VirtualFile file = path.getVirtualFile();

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

    VirtualFile vf = filePath.getVirtualFile();
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
    if( writableFiles.size() == 0 )
      return;

    ArrayList<String> writableExplicitFiles = new ArrayList<String>();
    final List<String> newFiles = new ArrayList<String>();

    //-------------------------------------------------------------------------
    //  Exclude those files for which status is known apriori:
    //  - file has status "changed" right after it was checked out
    //  - file has status "Merge Conflict" if that was indicated during
    //    the last commit operation.
    //-------------------------------------------------------------------------
    selectExplicitWritableFiles( writableFiles, writableExplicitFiles );

    List<String> refNames = new ArrayList<String>();
    for( String file : writableExplicitFiles )
    {
      String legalName = discoverOldName( file );
      refNames.add( legalName );
    }

    //-------------------------------------------------------------------------
    //  If we deal with Native implementation OR we analyze the status of a
    //  single file, then we have no need to use optimized scheme - just call
    //  current implementor once.
    //-------------------------------------------------------------------------
    if( !host.isCmdImpl() || writableExplicitFiles.size() == 1 )
    {
      LOG.info( "ChangeProvider - Analyzing writable files on per-file basis:" );
      for( int i = 0; i < writableExplicitFiles.size(); i++ )
      {
        LOG.info( "\t\t\t" + writableExplicitFiles.get( i ) );

        Status _status = host.getStatus( new File( refNames.get( i ) ) );
        if( _status == Status.NOT_AN_ELEMENT )
          newFiles.add( writableExplicitFiles.get( i ) );
        else
        if( _status == Status.CHECKED_OUT )
          filesChanged.add( writableExplicitFiles.get( i ) );
        else
        if( _status == Status.HIJACKED )
          filesHijacked.add( writableExplicitFiles.get( i ) );
      }
      LOG.info( "ChangeProvider - \"cleartool ls\" command finished" );
    }
    else
    {
      LOG.info( "ChangeProvider - Analyzing writables in batch mode using CLEARTOOL on " + writableFiles.size() + " files." );

      StatusMultipleProcessor processor = new StatusMultipleProcessor( refNames );
      processor.execute();
      LOG.info( "ChangeProvider - \"CLEARTOOL LS\" batch command finished." );

      for( int i = 0; i < writableExplicitFiles.size(); i++ )
      {
        if( processor.isNonexist( refNames.get( i ) ))
          newFiles.add( writableExplicitFiles.get( i ) );
        else
        if( processor.isCheckedout( refNames.get( i ) ))
          filesChanged.add( writableExplicitFiles.get( i ) );
        else
        if( processor.isHijacked( refNames.get( i ) ))
          filesHijacked.add( writableExplicitFiles.get( i ) );
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

      if( file.getUserData( SUCCESSFUL_CHECKOUT ) != null  )
      {
        //  Do not forget to delete this property right after the change
        //  is classified, otherwise this file will always be determined
        //  as modified.
        file.putUserData( SUCCESSFUL_CHECKOUT, null );
        filesChanged.add( file.getPath() );
      }
      else
      if( file.getUserData( MERGE_CONFLICT ) != null )
      {
        filesMerge.add( file.getPath() );
      }
      else
      {
        newWritables.add( path );
      }
    }
  }

  /**
   * For a given file which is known to be new, check also its direct parent
   * folder for presence in the VSS repository, and then all its indirect parent
   * folders until we reach project boundaries or find the existing folder.
  */
  private void analyzeParentFoldersForPresence( String file, List<String> newFolders,
                                                HashSet<String> processed )
  {
    String fileParent = new File( file ).getParentFile().getPath();
    String fileParentNorm = VcsUtil.getCanonicalLocalPath( fileParent );
    String refParentName = discoverOldName( fileParentNorm );

    if( host.fileIsUnderVcs( fileParent ) && !processed.contains( fileParent.toLowerCase() ) )
    {
      LOG.info( "ChangeProvider - Check potentially new folder" );
      
      processed.add( fileParent.toLowerCase() );
      if( !host.fileExistsInVcs( refParentName ))
      {
        LOG.info( "                 Folder [" + fileParent + "] is not in the repository" );
        newFolders.add( fileParent );
        analyzeParentFoldersForPresence( fileParent, newFolders, processed );
      }
    }
  }

  /**
   * File is either:
   * - "new" - it is not contained in the repository, but host contains
   *           a record about it (that is, it was manually moved to the
   *           list of files to be added to the commit.
   * - "unversioned" - it is not contained in the repository yet.
   */
  private void addAddedFiles( final ChangelistBuilder builder )
  {
    for( String fileName : filesNew )
    {
      //  In the case of file rename or parent folder rename we should
      //  refer to the list of new files by the
      String refName = discoverOldName( fileName );

      //  New file could be added AFTER and BEFORE the package rename.
      if( host.containsNew( fileName ) || host.containsNew( refName ))
      {
        FilePath path = VcsUtil.getFilePath( fileName );
        builder.processChange( new Change( null, new CurrentContentRevision( path ) ));
      }
      else
      {
        builder.processUnversionedFile( VcsUtil.getVirtualFile( fileName ) );
      }
    }
  }

  /**
   * Add all files which were determined to be changed (somehow - modified,
   * renamed, etc) and folders which were renamed.
   * NB: adding folders information actually works only in either batch refresh
   * of statuses or when some folder is in the list of changes.
   */
  private void addChangedFiles( final ChangelistBuilder builder )
  {
    for( String fileName : filesChanged )
    {
      String validRefName = discoverOldName( fileName );
      final FilePath refPath = VcsUtil.getFilePath( validRefName );
      final FilePath currPath = VcsUtil.getFilePath( fileName ); // == refPath if no rename occured

      builder.processChange( new Change( new CCaseContentRevision( host, refPath, project ), new CurrentContentRevision( currPath )));
    }

    for( String folderName : host.renamedFolders.keySet() )
    {
      String oldFolderName = host.renamedFolders.get( folderName );
      final FilePath refPath = VcsUtil.getFilePath( oldFolderName );
      final FilePath currPath = VcsUtil.getFilePath( folderName );

      builder.processChange( new Change( new CCaseContentRevision( host, refPath, project ), new CurrentContentRevision( currPath )));
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

  private void addMergeConflictFiles( final ChangelistBuilder builder )
  {
    for( String path : filesMerge )
    {
      final FilePath fp = VcsUtil.getFilePath( path );
      builder.processChange( new Change( new CCaseContentRevision( host, fp, project ), new CurrentContentRevision( fp ), FileStatus.MERGED_WITH_CONFLICTS ));
    }
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
    filesMerge.clear();
  }

  private boolean isBatchUpdate( VcsDirtyScope scope )
  {
    boolean isBatch = false;
    ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
    VirtualFile[] roots = mgr.getRootsUnderVcs( host );
    for( FilePath path : scope.getRecursivelyDirtyDirectories() )
    {
      for( VirtualFile root : roots )
      {
        isBatch = isBatch || (path.getVirtualFile().getPath().equals( root.getPath() ) );
      }
    }
    return isBatch;
  }
  
  private String discoverOldName( String file )
  {
    String oldName = host.renamedFiles.get( VcsUtil.getCanonicalLocalPath( file ) );
    if( oldName == null )
    {
      oldName = host.renamedFolders.get( VcsUtil.getCanonicalLocalPath( file ) );
      if( oldName == null )
      {
        oldName = findInRenamedParentFolder( file );
        if( oldName == null )
          oldName = file;
      }
    }

    return oldName;
  }

  private String findInRenamedParentFolder( String name )
  {
    String fileInOldFolder = name;
    for( String folder : host.renamedFolders.keySet() )
    {
      String oldFolderName = host.renamedFolders.get( folder );
      if( name.startsWith( folder ) )
      {
        fileInOldFolder = oldFolderName + name.substring( folder.length() );
        break;
      }
    }
    return fileInOldFolder;
  }

  private boolean isUnderRenamedFolder( String fileName )
  {
    for( String folder : host.renamedFolders.keySet() )
    {
      if( fileName.startsWith( folder ) )
        return true;
    }
    return false;
  }

  private boolean isFileCCaseProcessable( VirtualFile file )
  {
    return isValidFile( file ) && VcsUtil.isPathUnderProject( project, file.getPath() );
  }

  private static boolean isValidFile( VirtualFile file )
  {
    return (file != null) && file.isWritable() && !file.isDirectory();
  }

  private static String extMasks( Set<FilePath> scope )
  {
    HashMap<String, Integer> masks = new HashMap<String, Integer>();
    for( FilePath path : scope )
    {
      int index = path.getName().lastIndexOf( '.' );
      if( index != -1 )
      {
        String ext = path.getName().substring( index );
        Integer count = masks.get( ext );
        masks.put( ext, (count == null) ? 1 : (count.intValue() + 1 ) );
      }
    }

    String masksStr = "";
    for( String ext : masks.keySet() )
    {
      masksStr += ext + " - " + masks.get( ext ).intValue() + "; ";
    }
    return masksStr;
  }

  private void validateChangesOverTheHost( final VcsDirtyScope scope )
  {
    ApplicationManager.getApplication().invokeLater( new Runnable() {
      public void run() {
        HashSet<FilePath> set = new HashSet<FilePath>();
        set.addAll( scope.getDirtyFiles() );
        set.addAll( scope.getRecursivelyDirtyDirectories() );

        ProjectLevelVcsManager mgr = ProjectLevelVcsManager.getInstance( project );
        for( FilePath path : set )
        {
          AbstractVcs fileHost = mgr.getVcsFor( path );
          if( fileHost != host )
            throw new IllegalArgumentException( "Not valid scope for current Vcs: " + path.getPath() );
        }
      }
    });
  }
}
