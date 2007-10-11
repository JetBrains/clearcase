package net.sourceforge.transparent.Checkin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.TransparentVcs;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Jun 28, 2007
 */
public class CCaseRollbackEnvironment implements RollbackEnvironment
{
  @NonNls private static final String FILE_NOT_IN_VOB_SIG = "element name not found";
  
  @NonNls private static final String UPDATE_SUCC_PREFIX_1 = "Processing dir";
  @NonNls private static final String UPDATE_SUCC_PREFIX_2 = "Loading ";
  @NonNls private static final String UPDATE_SUCC_PREFIX_3 = "End dir";
  @NonNls private static final String UPDATE_SUCC_PREFIX_4 = "Done loading";
  @NonNls private static final String UPDATE_SUCC_PREFIX_5 = "Log has been written";
  @NonNls private static final String UPDATE_SUCC_PREFIX_6 = ".";
  @NonNls private static final String UPDATE_SUCC_PREFIX_7= "Making dir";

  private Project project;
  private TransparentVcs host;

  public CCaseRollbackEnvironment( Project project, TransparentVcs host )
  {
    this.project = project;
    this.host = host;
  }

  public String getRollbackOperationName() {
    return VcsBundle.message("changes.action.rollback.text");
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
        //  rename of the newFolderPath back to its former name, since we can't
        //  keep track of what consequent changes were done (due to Java
        //  semantics of the package rename).
        FilePath newFolderPath = change.getAfterRevision().getFile();
        File folderNew = newFolderPath.getIOFile();
        FilePath oldFolderPath = change.getBeforeRevision().getFile();
        File folderOld = oldFolderPath.getIOFile();
        
        folderNew.renameTo( folderOld );
        host.renamedFolders.remove( VcsUtil.getCanonicalLocalPath( folderNew.getPath() ) );

        processedFiles.add( oldFolderPath );
        VcsUtil.waitForTheFile( folderOld.getPath() );
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
      host.deleteNewFile( file.getVirtualFile() );
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

    String normPath = VcsUtil.getCanonicalLocalPath( path.getPath() );
    if( host.isFolderRemoved( normPath ) || host.isFolderRemovedForVcs( normPath ) )
    {
      //  For ClearCase to get back the locally removed folder, it is
      //  necessary to issue "Update" command. This will revert it to the
      //  state before the checking out on deletion.
      updateFile( path.getPath(), errors );

      //  In the case we restoring the folder which IS NOT in the repository
      //  (e.g. it was inproperly put into the list of deleted or it was already
      //  removed from the VOB), it is not restored locally and no update is
      //  needed. BUT: since it is not created, its record in the hash of removed
      //  folders is not removed too - thus remove it explicitely here.
      if( path.getIOFile().exists() )
      {
        //  If that folder contained checked out files, then they are not
        //  reverted back after the parent folder is updated. For each of
        //  file we need to issue "undocheckout" command.
        undocheckoutInFolder( path.getPath(), errors );
      }
      else
      {
        host.removeFolderFromDeleted( normPath );
      }
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

  private void undocheckoutInFolder( String path, List<VcsException> errors )
  {
    String output = TransparentVcs.cleartoolOnLocalPathWithOutput( path, "lsch", "-short", "-r" );
    TransparentVcs.LOG.info( output );

    String[] lines = LineTokenizer.tokenize( output, false );
    for( String line : lines )
    {
      File file = new File( path, line );
      host.undoCheckoutFile( file, errors );
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

  public void rollbackIfUnchanged(VirtualFile file) {
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
            line.startsWith( UPDATE_SUCC_PREFIX_5 ) || line.startsWith( UPDATE_SUCC_PREFIX_6 ) ||
            line.startsWith( UPDATE_SUCC_PREFIX_7 );
  }

}
