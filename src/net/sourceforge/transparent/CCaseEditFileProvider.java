package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import net.sourceforge.transparent.actions.CheckoutDialog;
import org.jetbrains.annotations.NonNls;

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
              checkOutOrHijackFile( file, errors );
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

  private void checkOutOrHijackFile( VirtualFile file, List<VcsException> errors ) throws VcsException {
    boolean toHijack = shouldHijackFile( file );
    try
    {
      if( toHijack )
        hijackFile( file );
      else
      {
        CCaseViewsManager mgr = CCaseViewsManager.getInstance( host.getProject() );
        boolean isUcmView = mgr.isUcmViewForFile( file );
        boolean hasActivity = (mgr.getActivityOfViewOfFile( file ) != null);
        boolean needToSetActivity = (isUcmView && !hasActivity);

        String comment = "";
        if( host.getCheckoutOptions().getValue() || needToSetActivity )
        {
          CheckoutDialog dialog = new CheckoutDialog( host.getProject(), file );
          dialog.show();
          if( dialog.getExitCode() == CheckoutDialog.CANCEL_EXIT_CODE )
            return;

          comment = dialog.getComment();
        }
        host.checkoutFile( file, false, comment );
      }
    }
    catch( Throwable e )
    {
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
