package net.sourceforge.transparent.ChangeManagement;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/19/12
 * Time: 7:23 PM
 */
public interface TransparentI {
  boolean isFileIgnored(final VirtualFile file);
  boolean isRenamedFile(final String path);
  boolean isRenamedFolder(final String path);
  boolean isCheckedOutFolder(final String path);
}
