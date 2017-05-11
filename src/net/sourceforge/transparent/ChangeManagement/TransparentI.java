package net.sourceforge.transparent.ChangeManagement;

import com.intellij.openapi.vfs.VirtualFile;

public interface TransparentI {
  boolean isFileIgnored(final VirtualFile file);
  boolean isRenamedFile(final String path);
  boolean isRenamedFolder(final String path);
  boolean isCheckedOutFolder(final String path);
}
