package x;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.vcs.DirectoryData;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import junit.framework.Assert;
import net.sourceforge.transparent.ChangeManagement.CCaseWriteableAndUnversionedCollector;
import net.sourceforge.transparent.ChangeManagement.TransparentI;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/19/12
 * Time: 7:44 PM
 */
public class CCaseWriteableAndUnversionedTest extends PlatformTestCase {
  private LocalFileSystem myLocalFileSystem;
  private IdeaProjectTestFixture myProjectFixture;
  private Project myProject;

  @Override
  public void setUp() throws Exception {
    myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getClass().getSimpleName()).getFixture();
    myProjectFixture.setUp();
    myProject = myProjectFixture.getProject();

    myLocalFileSystem = LocalFileSystem.getInstance();
  }

  @Override
  public void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        myProjectFixture.tearDown();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  public void testReadOnlyFileAndIgnoredFolder() throws Exception {
    final DirectoryData data = new DirectoryData(myProject.getBaseDir(), 3, 2, ".txt");
    try {
      data.clear();
      data.create();

      final VirtualFile vFileRo = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N0/FL00N0.txt"));
      Assert.assertTrue(vFileRo != null);
      FileUtil.setReadOnlyAttribute(vFileRo.getPath(), true);
      vFileRo.refresh(false, false);

      final MockTransparent mockTransparent = new MockTransparent();
      final VirtualFile vFolderIgnored = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N1/DL01N1"));
      Assert.assertTrue(vFolderIgnored != null);
      mockTransparent.addToIgnored(vFolderIgnored);

      final VirtualFile vFUnder1 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N1/DL01N1/FL011N0.txt"));
      final VirtualFile vFUnder2 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N1/DL01N1/FL011N1.txt"));
      Assert.assertTrue(vFUnder1 != null);
      Assert.assertTrue(vFUnder2 != null);

      mockTransparent.addToNeverAsk(vFUnder1);
      mockTransparent.addToNeverAsk(vFUnder2);

      final CCaseWriteableAndUnversionedCollector collector = new CCaseWriteableAndUnversionedCollector(myProject, mockTransparent);
      collector.collectWritableFiles(VcsUtil.getFilePath(myProject.getBaseDir()));

      final Set<String> ignored = collector.getFilesIgnored();
      Assert.assertEquals(1, ignored.size());
      Assert.assertEquals(vFolderIgnored.getPath(), ignored.iterator().next());

      final TreeSet<VirtualFile> dirs = collector.getDirs();
      //Assert.assertEquals(4, dirs.size());

      final VirtualFile vUnv1 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N0/DL00N0"));
      final VirtualFile vUnv2 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N0/DL00N1"));
      final VirtualFile vUnv3 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N1"));
      final VirtualFile vUnv31 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N1/DL01N0"));
      Assert.assertTrue(vUnv1 != null && vUnv2 != null && vUnv3 != null && vUnv31 != null);
      Assert.assertTrue(dirs.contains(vUnv1));
      Assert.assertTrue(dirs.contains(vUnv2));
      Assert.assertTrue(dirs.contains(vUnv3));
      Assert.assertTrue(dirs.contains(vUnv31));

      Assert.assertTrue(! dirs.contains(vFileRo.getParent()));
      Assert.assertTrue(! dirs.contains(vFolderIgnored));
      Assert.assertTrue(! dirs.contains(data.getBase()));
    } finally {
      data.clear();
    }
  }

  public void testCheckoutedFolder() throws Exception {
    final DirectoryData data = new DirectoryData(myProject.getBaseDir(), 3, 2, ".txt");
    try {
      data.clear();
      data.create();

      final VirtualFile vCheckedOut = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N1/DL01N1"));
      Assert.assertTrue(vCheckedOut != null);

      final MockTransparent mockTransparent = new MockTransparent();
      mockTransparent.checkOut(vCheckedOut.getPath());

      final CCaseWriteableAndUnversionedCollector collector = new CCaseWriteableAndUnversionedCollector(myProject, mockTransparent);
      collector.collectWritableFiles(VcsUtil.getFilePath(myProject.getBaseDir()));

      final Set<String> ignored = collector.getFilesIgnored();
      Assert.assertEquals(0, ignored.size());

      final TreeSet<VirtualFile> dirs = collector.getDirs();
      //Assert.assertEquals(4, dirs.size());

      final VirtualFile vUnv1 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N0"));
      final VirtualFile vUnv2 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N0/DL00N0"));
      final VirtualFile vUnv3 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N0/DL00N1"));
      final VirtualFile vUnv31 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N1/DL01N0"));
      Assert.assertTrue(vUnv1 != null && vUnv2 != null && vUnv3 != null && vUnv31 != null);
      Assert.assertTrue(dirs.contains(vUnv1));
      Assert.assertTrue(dirs.contains(vUnv2));
      Assert.assertTrue(dirs.contains(vUnv3));
      Assert.assertTrue(dirs.contains(vUnv31));

      Assert.assertTrue(! dirs.contains(vCheckedOut));
      Assert.assertTrue(! dirs.contains(vCheckedOut.getParent()));
      Assert.assertTrue(! dirs.contains(data.getBase()));
    } finally {
      data.clear();
    }
  }

  public void testReadOnlyBelow() throws Exception {
    final DirectoryData data = new DirectoryData(myProject.getBaseDir(), 3, 2, ".txt");
    try {
      data.clear();
      data.create();

      final VirtualFile vFileRo = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N1/DL01N1/FL011N0.txt"));
      Assert.assertTrue(vFileRo != null);
      FileUtil.setReadOnlyAttribute(vFileRo.getPath(), true);
      vFileRo.refresh(false, false);

      final MockTransparent mockTransparent = new MockTransparent();

      final CCaseWriteableAndUnversionedCollector collector = new CCaseWriteableAndUnversionedCollector(myProject, mockTransparent);
      collector.collectWritableFiles(VcsUtil.getFilePath(myProject.getBaseDir()));

      final Set<String> ignored = collector.getFilesIgnored();
      Assert.assertEquals(0, ignored.size());

      final TreeSet<VirtualFile> dirs = collector.getDirs();
      //Assert.assertEquals(4, dirs.size());

      final VirtualFile vUnv1 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N0"));
      final VirtualFile vUnv2 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N0/DL00N0"));
      final VirtualFile vUnv3 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N0/DL00N1"));
      final VirtualFile vUnv31 = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), "DL0N1/DL01N0"));
      Assert.assertTrue(vUnv1 != null && vUnv2 != null && vUnv3 != null && vUnv31 != null);
      Assert.assertTrue(dirs.contains(vUnv1));
      Assert.assertTrue(dirs.contains(vUnv2));
      Assert.assertTrue(dirs.contains(vUnv3));
      Assert.assertTrue(dirs.contains(vUnv31));

      Assert.assertTrue(! dirs.contains(vFileRo.getParent()));
      Assert.assertTrue(! dirs.contains(vFileRo.getParent().getParent()));
      Assert.assertTrue(! dirs.contains(data.getBase()));
    } finally {
      data.clear();
    }
  }

  private static class MockTransparent implements TransparentI {
    private final Set<VirtualFile> myIgnoredFiles;
    private final Set<VirtualFile> myNeverAskAbout;
    private final Set<String> myCheckedOutFolders;

    private MockTransparent() {
      myIgnoredFiles = new HashSet<>();
      myNeverAskAbout = new HashSet<>();
      myCheckedOutFolders = new HashSet<>();
    }

    public void addToIgnored(final VirtualFile file) {
      myIgnoredFiles.add(file);
    }

    public void addToNeverAsk(final VirtualFile file) {
      myNeverAskAbout.add(file);
    }

    public void checkOut(final String s) {
      myCheckedOutFolders.add(s);
    }

    @Override
    public boolean isFileIgnored(VirtualFile file) {
      Assert.assertTrue(! myNeverAskAbout.contains(file));
      return myIgnoredFiles.contains(file);
    }

    @Override
    public boolean isRenamedFile(String path) {
      return false;
    }

    @Override
    public boolean isRenamedFolder(String path) {
      return false;
    }

    @Override
    public boolean isCheckedOutFolder(String path) {
      return myCheckedOutFolders.contains(path);
    }
  }
}
