package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import net.sourceforge.transparent.actions.CheckoutDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Dec 19, 2006
 */
public class CCaseEditFileProvider implements EditFileProvider
{
  @NonNls private final static String REQUEST_TEXT = "Would you like to invoke 'CheckOut' command?";
  @NonNls private final static String FAIL_RO_TEXT = "Can not set R/O attribute for file: ";
  @NonNls private final static String FAIL_DIALOG_TITLE = "Operation Failed";

  private final TransparentVcs host;

  public CCaseEditFileProvider(TransparentVcs host )
  {
    this.host = host;
  }

  public String getRequestText() {  return REQUEST_TEXT;  }

  public void editFiles( final VirtualFile[] files ) throws VcsException {
    final List<VcsException> errors = new ArrayList<VcsException>();
    final ChangeListManager mgr = ChangeListManager.getInstance( host.getProject() );

    final CurrentStatusHelper[] statusHelper = new CurrentStatusHelper[1];
    final boolean succeeded = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          statusHelper[0] = preProcessFiles(files);
        }
      }, "ClearCase checkout: preprocessing files", true, host.getProject());
    if (! succeeded || statusHelper[0] == null) return;
    final String comment = getEditComment(files, statusHelper[0]);
    if (comment == null) return;  // was cancelled
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        int cnt = 0;
        for( VirtualFile file : files )
        {
          final boolean ignoredFile = mgr.isIgnoredFile(file);
          final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          if (indicator != null) {
            indicator.checkCanceled();
            indicator.setText2((ignoredFile ? "Ignored: " : "Checking out: ") + getFileDescriptionForProgress(file));
            indicator.setFraction((double) cnt/files.length);
          }
          ++ cnt;
          if(! ignoredFile) {
            try {
              statusHelper[0].checkOutOrHijackFile(file, errors, comment);
            }
            catch (VcsException e) {
              return;
              // exit, exception already kept
            }
          }
        }
      }
    }, "Checkout files", true, host.getProject());
    if( errors.size() > 0 )
    {
      throw errors.get(0);
    }
  }

  private static String getFileDescriptionForProgress(final VirtualFile file) {
    final VirtualFile parent = file.getParent();
    return file.getName() + " (" + (parent == null ? file.getPath() : parent.getPath()) + ")";
  }

  @Nullable
  private String getEditComment(final VirtualFile[] files, CurrentStatusHelper statusHelper) {
    CCaseViewsManager mgr = CCaseViewsManager.getInstance( host.getProject() );

    boolean askComment = host.getCheckoutOptions().getValue();
    if (! askComment && CCaseSharedConfig.getInstance(host.getProject()).isUseUcmModel()) {
      for (VirtualFile file : files) {
        if (statusHelper.shouldHijack(file)) continue;
        boolean isUcmView = mgr.isUcmViewForFile( file );
        boolean hasActivity = (mgr.getActivityOfViewOfFile( file ) != null);
        askComment = (isUcmView && ! hasActivity);
        if (askComment) break;
      }
    }

    String comment = "";
    if(askComment) {
      CheckoutDialog dialog = ( files.length == 1 ) ?
                              new CheckoutDialog( host.getProject(), files[ 0 ] ) :
                              new CheckoutDialog( host.getProject(), files );
      dialog.show();
      if( dialog.getExitCode() == CheckoutDialog.CANCEL_EXIT_CODE )
        return null;

      comment = dialog.getComment();
    }
    if (! StringUtil.isEmptyOrSpaces(comment)) {
      VcsConfiguration.getInstance(host.getProject()).saveCommitMessage(comment);
    }
    return comment;
  }

  public static void hijackFile( final VirtualFile file ) throws VcsException
  {
    WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              ReadOnlyAttributeUtil.setReadOnlyAttribute(file, false);
            }
            catch (IOException e) {
              Messages.showErrorDialog(FAIL_RO_TEXT + file.getPath(), FAIL_DIALOG_TITLE);
            }
          }
        });
      }
    });
  }

  private CurrentStatusHelper preProcessFiles(final VirtualFile[] files) {
    final CurrentStatusHelper csh = new CurrentStatusHelper(host);
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
    if (pi != null) {
      pi.setIndeterminate(false);
    }
    int cnt = 0;
    for (VirtualFile file : files) {
      if (pi != null) {
        pi.checkCanceled();
        pi.setFraction((double) cnt / files.length);
        pi.setText(getFileDescriptionForProgress(file));
      }
      ++ cnt;
      final String oldName = host.discoverOldName(file.getPath());
      if (oldName != null) {
        csh.addRenamed(file, oldName);
        if (host.getConfig().isOffline()) {
          csh.shouldHijack(file);
          continue;
        }
        final Status oldStatus = host.getStatusSafely(new File(oldName));
        if (Status.NOT_AN_ELEMENT.equals(oldStatus)) {
          csh.shouldHijack(file);
        }
      } else {
        if (shouldHijackFile(file)) {
          csh.shouldHijack(file);
        }
      }
    }
    return csh;
  }

  private static class CurrentStatusHelper {
    private final Map<VirtualFile, String> myRenamedMap;
    private final Set<VirtualFile> myShouldHijackFiles;
    private final TransparentVcs host;

    CurrentStatusHelper(final TransparentVcs host) {
      this.host = host;
      myRenamedMap = new HashMap<VirtualFile, String>();
      myShouldHijackFiles = new HashSet<VirtualFile>();
    }

    public void addRenamed(final VirtualFile file, final String oldName) {
      myRenamedMap.put(file, oldName);
    }

    public void unversioned(final VirtualFile file) {
      myShouldHijackFiles.add(file);
    }

    public String getOldName(final VirtualFile file) {
      return myRenamedMap.get(file);
    }

    public boolean shouldHijack(final VirtualFile file) {
      return myShouldHijackFiles.contains(file);
    }

    public void checkOutOrHijackFile(VirtualFile file, List<VcsException> errors, String comment) throws VcsException {
      boolean toHijack = shouldHijack(file);
      try {
        if(toHijack) {
          hijackFile(file);
        } else {
          final String oldName = myRenamedMap.get(file);
          if (oldName != null && ! file.getPath().equals(FileUtil.toSystemIndependentName(oldName))) {
            final File oldFile = new File(oldName);
            host.checkoutFile(oldFile, false, comment, true, true);
            hijackFile(file);
          } else {
            host.checkoutFile(file, false, comment);
          }
        }
      } catch( Throwable e ) {
        final VcsException e1 = new VcsException(e.getMessage());
        errors.add(e1);
        throw e1;
      }
    }
  }

  private boolean shouldHijackFile( VirtualFile file )
  {
    return  host.getConfig().isOffline() || 
           (host.getStatus( file ) == Status.NOT_AN_ELEMENT);
  }
}
