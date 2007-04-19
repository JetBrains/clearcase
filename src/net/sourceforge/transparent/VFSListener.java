package net.sourceforge.transparent;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * User: lloix
 * Date: Sep 21, 2006
 */
public class VFSListener extends VirtualFileAdapter
{
  private Project project;
  private TransparentVcs  host;

  public VFSListener( Project project )
  {
    this.project = project;
    host = TransparentVcs.getInstance( project ); 
  }

  public void beforeFileMovement(VirtualFileMoveEvent event)
  {
    VirtualFile file = event.getFile();

    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( file, project, host ))
      return;

    if( !file.isDirectory() )
    {
      String oldName = file.getPath();
      String newName = event.getNewParent().getPath() + "/" + file.getName();

      String prevName = host.renamedFiles.get( oldName );
      if( host.fileIsUnderVcs( oldName ) || prevName != null )
      {
        //  Newer name must refer to the oldest one in the chain of movements
        if( prevName == null )
          prevName = oldName;

        //  Check whether we are trying to rename the file back -
        //  if so, just delete the old key-value pair
        if( !prevName.equals( newName ) )
          host.renamedFiles.put( newName, prevName );

        host.renamedFiles.remove( oldName );
      }
    }
  }

  public void beforePropertyChange(VirtualFilePropertyEvent event)
  {
    VirtualFile file = event.getFile();

    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( file, project, host ))
      return;

    if( event.getPropertyName() == VirtualFile.PROP_WRITABLE )
    {
      //  If user managed to perform maerge on the file outside the
      //  environment, clear this mark so that we will not confuse ourselves.
      file.putUserData( TransparentVcs.MERGE_CONFLICT, null );
    }
    else
    if( event.getPropertyName() == VirtualFile.PROP_NAME )
    {
      String parentDir = file.getParent().getPath() + "/";
      String oldName = parentDir + event.getOldValue();
      String newName = parentDir + event.getNewValue();

      performRename( file.isDirectory() ? host.renamedFolders : host.renamedFiles, oldName, newName );
    }
  }

  private static void performRename( HashMap<String, String> store, String oldName, String newName )
  {
    //  Newer name must refer to the oldest name in the chain of renamings
    String prevName = store.get( oldName );
    if( prevName == null )
      prevName = oldName;

    //  Check whether we are trying to rename the file back - if so,
    //  just delete the old key-value pair
    if( !prevName.equals( newName ) )
      store.put( newName, prevName );

    store.remove( oldName );
  }

  public void fileCreated( VirtualFileEvent event )
  {
    @NonNls final String TITLE = "Add file(s)";
    @NonNls final String MESSAGE = "Do you want to schedule the following file for addition to ClearCase?\n{0}";

    VirtualFile file = event.getFile();
    String path = file.getPath();

    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if( !VcsUtil.isFileForVcs( file, project, host ))
      return;

    //  In the case when the project content is synchronized over the
    //  occasionally removed files.
    host.removedFiles.remove( path );
    host.removedFolders.remove( path );
    host.deletedFiles.remove( path );
    host.deletedFolders.remove( path );

    //  Do not ask user if the files created came from the vcs per se
    //  (obviously they are not new).
    if( event.isFromRefresh() )
      return;

    //  Take into account only processable files.

    if( isFileProcessable( file ))
    {
      VcsShowConfirmationOption confirmOption = host.getAddConfirmation();

      //  In the case when we need to perform "Add" vcs action right upon
      //  the file's creation, put the file into the host's cache until it
      //  will be analyzed by the ChangeProvider.
      if( confirmOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY )
        host.add2NewFile( path );
      else
      if( confirmOption.getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION )
      {
        List<VirtualFile> files = new ArrayList<VirtualFile>();
        files.add( file );
        Collection<VirtualFile> filesToProcess = AbstractVcsHelper.getInstance( project ).selectFilesToProcess( files, TITLE, null, TITLE,
                                                                                                                MESSAGE, confirmOption );
        if( filesToProcess != null )
          host.add2NewFile( path );
      }
    }
  }

  public void beforeFileDeletion( VirtualFileEvent event )
  {
    @NonNls final String TITLE = "Delete file(s)";
    @NonNls final String MESSAGE = "Do you want to schedule the following file for removal from ClearCase?\n{0}";

    VirtualFile file = event.getFile();
    
    //  Do not ask user if the files deletion is caused by the vcs operation
    //  like UPDATE (obviously they are deleted without a necessity to recover
    //  or to keep track).
    if( event.isFromRefresh() )
      return;

    FileStatus status = FileStatusManager.getInstance( project ).getStatus( file );
    if( host.fileIsUnderVcs( file.getPath() ) &&
        ( status != FileStatus.ADDED ) && ( status != FileStatus.UNKNOWN ))
    {
      VcsShowConfirmationOption confirmOption = host.getRemoveConfirmation();

      //  In the case when we need to perform "Delete" vcs action right upon
      //  the file's deletion, put the file into the host's cache until it
      //  will be analyzed by the ChangeProvider.
      if( confirmOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY )
      {
        markFileRemoval( file, host.deletedFolders, host.deletedFiles );
      }
      else
      if( confirmOption.getValue() == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY )
      {
        markFileRemoval( file, host.removedFolders, host.removedFiles );
      }
      else
      {
        List<VirtualFile> files = new ArrayList<VirtualFile>();
        files.add( event.getFile() );
        Collection<VirtualFile> filesToProcess = AbstractVcsHelper.getInstance( project ).selectFilesToProcess( files, TITLE, null, TITLE,
                                                                                                                MESSAGE, confirmOption );
        if( filesToProcess != null )
          markFileRemoval( file, host.deletedFolders, host.deletedFiles );
        else
          markFileRemoval( file, host.removedFolders, host.removedFiles );
      }
    }
  }

  private void markFileRemoval( VirtualFile file, HashSet<String> folders, HashSet<String> files )
  {
    String path = file.getPath();
    if( file.isDirectory() )
    {
      markSubfolderStructure( path );
      folders.add( path );
    }
    else
      files.add( path );
  }

  /**
   * When adding new path into the list of the removed folders, remove from
   * that list all files/folders which were removed previously locating under
   * the given one (including it).
   */
  private void  markSubfolderStructure( String path )
  {
    for( Iterator<String> it = host.removedFiles.iterator(); it.hasNext(); )
    {
      String strFile = it.next();
      if( strFile.startsWith( path ) )
       it.remove();
    }
    for( Iterator<String> it = host.removedFolders.iterator(); it.hasNext(); )
    {
      String strFile = it.next();
      if( strFile.startsWith( path ) )
       it.remove();
    }
  }

  /**
   * File is not processable if it is outside the vcs scope or it is in the 
   * list of excluded project files.
   */
  private boolean isFileProcessable( VirtualFile file )
  {
    return !host.isFileIgnored( file ) &&
           !FileTypeManager.getInstance().isFileIgnored( file.getName() );
  }
}
