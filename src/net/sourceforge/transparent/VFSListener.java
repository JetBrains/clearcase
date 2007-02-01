package net.sourceforge.transparent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Sep 21, 2006
 * Time: 6:47:10 PM
 * To change this template use File | Settings | File Templates.
 */
public class VFSListener extends VirtualFileAdapter
{
  private Project project;
  private TransparentVcs  host;

  public VFSListener( Project project, TransparentVcs host )
  {  this.project = project; this.host = host; }

  public void beforeFileMovement(VirtualFileMoveEvent event)
  {
    if( !event.getFile().isDirectory() )
    {
      String oldName = event.getFile().getPath();
      String newName = event.getNewParent().getPath() + "/" + event.getFile().getName();

      String prevName = host.renamedFiles.get( oldName );
      if( host.fileIsUnderVcs( event.getFile() ) || prevName != null )
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
    if( event.getPropertyName() == VirtualFile.PROP_NAME )
    {
      //  When a folder is renamed (e.g. as the result of the "rename package"
      //  refactoring), we do not support renaming in the "Changes" dataflow,
      //  so we emulate that by "deleting" old subdirectory structure and
      //  marking the renamed one as new.
      //  Rename of files is supported as usual.
      if( file.isDirectory() )
      {
        host.removedFolders.add( file.getPath() );
        markSubfolderStructure( file.getPath() );

        //  During folder rename the sequence of the actions is as follows:
        //  - files under this folder are checked out (if they are not yet)
        //  - they are changed
        //  - folder is renamed
        //  So as input to the ChangeProvider we have not very complete
        //  information about what have been done actually. Since we emulate
        //  package rename with "removed/add package" all files under the
        //  renamed folder must be marked as "new" (as a new request).
        VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
        mgr.dirDirtyRecursively( file, true );
      }
      else
      {
        String parentDir = file.getParent().getPath() + "/";
        String oldName = parentDir + event.getOldValue();
        String newName = parentDir + event.getNewValue();

        //  Do not react on files which are not under this vcs
        if( host.fileIsUnderVcs( file ))
        {
          //  Newer name must refer to the oldest name in the chain of renamings
          String prevName = host.renamedFiles.get( oldName );
          if( prevName == null )
            prevName = oldName;

          //  Check whether we are trying to rename the file back - if so,
          //  just delete the old key-value pair
          if( !prevName.equals( newName ) )
            host.renamedFiles.put( newName, prevName );

          host.renamedFiles.remove( oldName );
        }
      }
    }
  }

  public void fileCreated(VirtualFileEvent event)
  {
    String path = event.getFile().getPath();

    //  In the case when the project content is synchronized over the
    //  occasionally removed files.
    host.removedFiles.remove( path );
    host.removedFolders.remove( path );

    //  Take into account only processable files. Do not pay attention on all
    //  other file which can be created as e.g. build process artifacts.

    if( !host.isFileIgnored( event.getFile() ) )
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
        files.add( event.getFile() );
        Collection<VirtualFile> filesToProcess = AbstractVcsHelper.getInstance( project ).selectFilesToProcess( files, "Add file(s) to the ClearCase?", null,
                                                                       "Add file(s) to the ClearCase?",
                                                                       "Do you want to schedule the following file for addition to Subversion?\n{0}",
                                                                       confirmOption );
        if( filesToProcess != null )
          host.add2NewFile( path );
      }
    }
  }

  public void beforeFileDeletion(VirtualFileEvent event)
  {
    VirtualFile file = event.getFile();
    FileStatus status = FileStatusManager.getInstance(project).getStatus( file );
    if( host.fileIsUnderVcs( file ) &&
        ( status != FileStatus.ADDED ) && ( status != FileStatus.UNKNOWN ))
    {
      if( file.isDirectory() )
      {
        String path = file.getPath();
        markSubfolderStructure( path );
        host.removedFolders.add( path );
      }
      else
        host.removedFiles.add( file.getPath());
    }
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
}
