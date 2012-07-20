package net.sourceforge.transparent.ChangeManagement;

import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VirtualFileFilter;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/19/12
 * Time: 7:17 PM
 */
public class CCaseWriteableAndUnversionedCollector {
  private final Project myProject;
  private final TransparentI myTransparentI;
  private final Set<String> myFilesWritable;
  private final Set<String> myFilesIgnored;
  private final TreeSet<VirtualFile> myDirs;

  public CCaseWriteableAndUnversionedCollector(Project project, final TransparentI transparentI) {
    myProject = project;
    myTransparentI = transparentI;
    myDirs = new TreeSet<VirtualFile>(FilePathComparator.getInstance());
    myFilesIgnored = new HashSet<String>();
    myFilesWritable = new HashSet<String>();
  }

  public Set<String> getFilesWritable() {
    return myFilesWritable;
  }

  public Set<String> getFilesIgnored() {
    return myFilesIgnored;
  }

  public TreeSet<VirtualFile> getDirs() {
    return myDirs;
  }

  /**
   * Iterate over the project structure and collect two types of files:
   * - writable files, they are the subject for subsequent analysis
   * - "ignored" files - which will be shown in a separate changes folder.
   */
  public void collectWritableFiles( final FilePath filePath )
  {
    final VirtualFile vf = filePath.getVirtualFile();
    if( vf != null )
    {
      final ArrayDeque<VirtualFile> chain = new ArrayDeque<VirtualFile>();
      final Map<VirtualFile, Boolean> chainROFlag = new HashMap<VirtualFile, Boolean>();

      ProjectLevelVcsManager.getInstance(myProject).iterateVcsRoot( vf, new Processor<FilePath>()
      {
        public boolean process(final FilePath file)
        {
          final String path = file.getPath().replace('\\', '/');
          final VirtualFile vFile = file.getVirtualFile();
          if (vFile == null || ! vFile.isValid()) return true;
          if (myTransparentI.isFileIgnored(vFile)) {
            myFilesIgnored.add(path);
            return true;
          }

          if (CCaseChangeProvider.isValidFile(vFile)) {
            myFilesWritable.add(path);
          } else if (! vFile.isDirectory()) {
            if (! Boolean.TRUE.equals(chainROFlag.get(vFile.getParent())) && ! vFile.isWritable() && ! myTransparentI.isRenamedFile(vFile.getPath())) {
              // then remove parents from unversioned; only check if not yet met before
              chainROFlag.put(vFile.getParent(), true);
            }
          } else {
            final boolean versioned = directoryIsVersioned(vFile);
            if (! versioned) {
              // directory probably unversioned
              myDirs.add(vFile);
              chain.addFirst(vFile);
            } else if (! Boolean.TRUE.equals(chainROFlag.get(vFile)) && ! chain.isEmpty()) {
              // directory is versioned
              chainROFlag.put(vFile, true);
            }
          }
          return true;
        }
      },
      new VirtualFileFilter() {
        @Override
        public boolean shouldGoIntoDirectory(@NotNull VirtualFile file) {
          final boolean ignored = myTransparentI.isFileIgnored(file);
          if (ignored) {
            myFilesIgnored.add(file.getPath());
          }
          return !ignored;
        }

        @Override
        public void afterChildrenVisited(@NotNull VirtualFile file) {
          if (! file.isDirectory()) return;
          // also there is a case when a directory is detected as checked out -> remove its parents from unversioned...
          final Boolean ro = chainROFlag.get(file);
          if (Boolean.TRUE.equals(ro)) {
            removeParentsFromUnversioned(file);
          }
          if (chain.getFirst().equals(file)){
            chain.removeFirst();
          }
          chainROFlag.remove(file);
        }
      });
    }
  }

  private boolean directoryIsVersioned(@NotNull final VirtualFile virtualFile) {
    final String dirPath = virtualFile.getPath();
    return Boolean.TRUE.equals(virtualFile.getUserData(CCaseChangeProvider.ourVersionedKey)) ||
           myTransparentI.isRenamedFolder(dirPath) || myTransparentI.isCheckedOutFolder(dirPath);
  }

  private void removeParentsFromUnversioned(VirtualFile vFile) {
    VirtualFile floor = myDirs.floor(vFile);
    if (floor == null) return;
    Iterator<VirtualFile> iterator = myDirs.headSet(floor, true).iterator();
    while (iterator.hasNext()) {
      VirtualFile next = iterator.next();
      if (VfsUtil.isAncestor(next, vFile, false)) {
        iterator.remove();
      }
    }
  }
}
