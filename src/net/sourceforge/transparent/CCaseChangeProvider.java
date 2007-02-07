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
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Dec 6, 2006
 * Time: 5:35:28 PM
 * To change this template use File | Settings | File Templates.
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
    LOG.info( "   Is project default? " + project.isDefault() );

    boolean isBatchUpdate = dirtyScope.getRecursivelyDirtyDirectories().size() > 0;
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
    }
    catch( ClearCaseException e )
    {
      final String message = FAIL_2_CONNECT_MSG + e.getMessage();
      ApplicationManager.getApplication().invokeLater( new Runnable() { public void run() { VcsUtil.showErrorMessage( project, message, FAIL_2_CONNECT_TITLE ); } });
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
    analyzeWritableFiles( path, writableFiles );
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
            if( isFileCCaseProcessable( file ) )
            {
              String path = file.getPath();
              writableFiles.add( path );
            }
            return true;
          }
        } );
    }
  }

  private void analyzeWritableFiles( FilePath filePath, List<String> writableFiles )
  {
    final List<String> newFiles = new ArrayList<String>();
    final List<String> newFolders = new ArrayList<String>();

//    if( writableFiles.size() < PER_FILE_DIFF_MARGIN )
    {
      LOG.info( "rem ChangeProvider - Analyzing writable files on per-file basis" );
      for( String path : writableFiles )
      {
        LOG.info( "rem ChangeProvider - Issue \"PROPERTIES\" command for getting information on writable file" );
        VirtualFile file = VcsUtil.getVirtualFile( path );
        ClearCaseFile ccFile = new ClearCaseFile( file, host.getClearCase() );

        if( ccFile.isElement() )
        {
          if( ccFile.isHijacked() )
            filesHijacked.add( path );
          else
            filesChanged.add( path );
        }
        else
          newFiles.add( path );
      }
      LOG.info( "rem ChangeProvider - \"PROPERTIES\" command finished" );
    }
    /*
    else
    {
      LOG.info( "rem ChangeProvider - Analyzing writable files on the base of \"Directory\" command" );

      ArrayList<VcsException> errors = new ArrayList<VcsException>();
      DirectoryCommand cmd = new DirectoryCommand( project, filePath.getPath(), errors );
      cmd.execute();

      for( String path : writableFiles )
      {
        path = path.toLowerCase();
        if( !cmd.projectFiles.contains( path ) )
          newFiles.add( path );
        else
          filesChanged.add( path );
      }
    }
    */

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
    /*
    String fileParent = new File( file ).getParentFile().getPath();

    if( VssUtil.isPathUnderProject( project, fileParent ) && !processedFolders.contains( fileParent ) )
    {
      LOG.info( "rem ChangeProvider - Issue \"PROPERTIES\" command for getting information on potentially new folder" );

      processedFolders.add( fileParent );
      String fileParentCanonical = VssUtil.getCanonicalLocalPath( fileParent );
      PropertiesCommand cmd = new PropertiesCommand( project, fileParentCanonical, true );
      cmd.execute();
      LOG.info( "rem ChangeProvider - \"PROPERTIES\" command finished" );

      if( !cmd.isValidRepositoryObject() )
      {
        newFolders.add( fileParentCanonical );
        foldersAbcent.add( fileParent );

        analyzeParentFolderStructureForPresence( fileParent, newFolders, processedFolders );
      }
    }
    */
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
      //-----------------------------------------------------------------------
      //  Do not process files which have RO status at all.
      //  Generally it means that all files which were got through some sort of
      //  "Get Latest Version" or "Update" are not processed at all, especially
      //  since there is no necessity in that. All other cases - modified and
      //  new files are processed as usual.
      //-----------------------------------------------------------------------
      VirtualFile file = VcsUtil.getVirtualFile( path.getPath() );
      String fileName = path.getPath();

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
            ClearCaseFile ccFile = new ClearCaseFile( file, host.getClearCase() );
            if( ccFile.isElement() )
            {
              if( ccFile.isHijacked() )
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
//      if( VssUtil.isPathUnderProject( project, path ) )
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
  }

  private void addChangedFiles( final ChangelistBuilder builder )
  {
    for( String path : filesChanged )
    {
      final FilePath fp = VcsUtil.getFilePath( path );
      builder.processChange( new Change( new CCaseRevision( fp, project ), new CurrentContentRevision( fp )));
    }

    for( String path : filesHijacked )
    {
      final FilePath fp = VcsUtil.getFilePath( path );
      builder.processChange( new Change( new CCaseRevision( fp, project ), new CurrentContentRevision( fp ), FileStatus.HIJACKED ));
    }
  }

  private void addRemovedFiles( final ChangelistBuilder builder )
  {
    /*
    for( String path : host.removedFolders )
      builder.processLocallyDeletedFile( VcsUtil.getFilePath( path ) );

    for( String path : host.removedFiles )
      builder.processLocallyDeletedFile( VcsUtil.getFilePath( path ) );
    */
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
    foldersAbcent.clear();
  }

  private boolean isFileCCaseProcessable( VirtualFile file )
  {
    return (file != null) && file.isWritable() && !file.isDirectory() &&
           VcsUtil.isPathUnderProject( project, file.getPath() ) &&
//           host.getFileFilter().accept( file ) &&
           !host.isFileIgnored( file );
//         !config.isFileExcluded( file.getName() );
//         && regularFileFilter.accept(file)
          /*
           VssUtil.isUnderVss( file, project ) &&
          */
  }

  private class CCaseRevision implements ContentRevision
  {
    @NonNls private static final String TMP_FILE_NAME = "idea_ccase";

    private VirtualFile file;
    private FilePath    revisionPath;
    private Project     project;
    private String      myServerContent;

    public CCaseRevision( FilePath path, Project proj )
    {
      revisionPath = path;
      project = proj;

      file = path.getVirtualFile();
    }

    @NotNull public VcsRevisionNumber getRevisionNumber()  {  return VcsRevisionNumber.NULL;   }
    @NotNull public FilePath getFile()                     {  return revisionPath; }

    public String getContent()
    {
      if( myServerContent == null )
        myServerContent = getServerContent();

      return myServerContent;
    }

    private String getServerContent()
    {
      @NonNls final String TITLE = "Error";
      @NonNls final String EXT = ".tmp";
      String content = "";

      //  For files which are in the project but reside outside the repository
      //  root their base revision version content is not defined (NULL).

      if( host.fileIsUnderVcs( file ))
      {
        try
        {
          //-------------------------------------------------------------------
          //  Since CCase does not allow us to get the latest content of a file
          //  from the repository, we need to get the VERSION string which characterizes
          //  this latest (or any other) version.
          //  Using this version string we can construct actual request to the
          //  "Get" command:
          //  "get -to <dest_file> <repository_file>@@<version>"
          //-------------------------------------------------------------------
          
          File tmpFile = File.createTempFile( TMP_FILE_NAME, EXT );
          tmpFile.deleteOnExit();
          File tmpDir = tmpFile.getParentFile();
          File myTmpFile = new File( tmpDir, Long.toString( new Date().getTime()) );

          String out = TransparentVcs.cleartoolWithOutput( "describe", file.getPath() );
          String version = parseLastRepositoryVersion( out );
          if( version != null )
          {
            final String out2 = TransparentVcs.cleartoolWithOutput( "get", "-to", myTmpFile.getPath(), file.getPath() + "@@" + version );

            //  We expect that properly finished command produce no (error or
            //  warning) output.
            if( out2.length() > 0 )
            {
              ApplicationManager.getApplication().invokeLater( new Runnable() { public void run() { VcsUtil.showErrorMessage( project, out2, TITLE ); } });
            }
            else
            {
              content = VcsUtil.getFileContent( myTmpFile );
              myTmpFile.delete();
            }
          }
        }
        catch( Exception e )
        {
          VcsUtil.showErrorMessage( project, e.getMessage(), TITLE );
        }
      }

      return content;
    }

    //-------------------------------------------------------------------------
    //  The sample format of the "DESCRIBE" command output is as follows below:
    //  --
    //  version "Foo22.java@@\main\p1_Integration\lloix_p1\CHECKEDOUT" from \main\p1_Integration\lloix_p1\0 (unreserved)
    //    checked out 19-Jan-07.12:53:23 by lloix.Domain Users@greYWolf
    //    by view: lloix_IrinaVobProjects ("GREYWOLF:D:\Projects\Test Projects\cc-tut\lloix_snapview2\lloix_IrinaVobProjects.vws")
    //    "sss"
    //    Element Protection:
    //      User : LABS\Irina.Petrovskaya : r--
    //      Group: LABS\Domain Users : r--
    //      Other:          : r--
    //    element type: text_file
    //    predecessor version: \main\p1_Integration\lloix_p1\0
    //    Attached activities:
    //      activity:Added@\irinaVOB  "Test all operations in Cleartool mode"
    //  --
    //  In order to retrieve the latest version in the repository, we seek for
    //  string "predecessor version:" in this log and extract the version string
    //  in the CCase format.
    //-------------------------------------------------------------------------
    private String parseLastRepositoryVersion( String text )
    {
      @NonNls final String SIG = "predecessor version:";
      String version = null;
      String[] lines = text.split( "\n" );
      for( String line : lines )
      {
        int index = line.indexOf( SIG );
        if( index != -1 )
        {
          version = line.substring( index + SIG.length() ).trim();
          break;
        }
      }
      return version;
    }
  }
}
