package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import net.sourceforge.transparent.actions.CheckoutDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    final String comment = getEditComment(files);
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
            final VirtualFile parent = file.getParent();
            indicator.setText2((ignoredFile ? "Ignored: " : "Checking out: ") + file.getName() +
              " (" + (parent == null ? file.getPath() : parent.getPath()) + ")");
            indicator.setFraction((double) cnt/files.length);
          }
          ++ cnt;
          if(! ignoredFile) {
            try {
              checkOutOrHijackFile( file, errors , comment);
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

  @Nullable
  private String getEditComment(final VirtualFile[] files) {
    CCaseViewsManager mgr = CCaseViewsManager.getInstance( host.getProject() );

    boolean askComment = host.getCheckoutOptions().getValue();
    if (! askComment && CCaseSharedConfig.getInstance(host.getProject()).isUseUcmModel()) {
      for (VirtualFile file : files) {
        if (shouldHijackFile(file)) continue;
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

  private void checkOutOrHijackFile(VirtualFile file, List<VcsException> errors, String comment) throws VcsException {
    boolean toHijack = shouldHijackFile( file );
    try {
      if(toHijack) {
        hijackFile(file);
      } else {
        host.checkoutFile(file, false, comment);
      }
    } catch( Throwable e ) {
      final VcsException e1 = new VcsException(e.getMessage());
      errors.add(e1);
      throw e1;
    }
  }

  public static void hijackFile( final VirtualFile file ) throws VcsException
  {
    ApplicationManager.getApplication().runWriteAction( new Runnable() { public void run(){
      try {   ReadOnlyAttributeUtil.setReadOnlyAttribute( file, false );  }
      catch( IOException e ) {
        Messages.showErrorDialog( FAIL_RO_TEXT + file.getName(), FAIL_DIALOG_TITLE );
      }
    } });
  }

  private boolean shouldHijackFile( VirtualFile file )
  {
    return  host.getConfig().isOffline() || 
           (host.getStatus( file ) == Status.NOT_AN_ELEMENT);
  }
}
