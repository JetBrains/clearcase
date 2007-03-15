package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

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

  private TransparentVcs host;

  public CCaseEditFileProvider(TransparentVcs host )
  {
    this.host = host;
  }

  public String getRequestText() {  return REQUEST_TEXT;  }

  public void editFiles( VirtualFile[] files )
  {
    ChangeListManager mgr = ChangeListManager.getInstance( host.getProject() );
    for( VirtualFile file : files )
    {
      if( !mgr.isIgnoredFile( file ) )
        checkOutOrHijackFile(file);
    }
  }

  private void checkOutOrHijackFile( VirtualFile file )
  {
    try
    {
      if( shouldHijackFile( file ) )
        hijackFile( file );
      else
        host.checkoutFile( file, false );
    }
    catch( Throwable e )
    {
      String message = "Exception while " + (shouldHijackFile(file) ? "hijacking " : "checking out ") +
                       file.getPresentableUrl() + "\n" + e.getMessage();
      Messages.showErrorDialog( message, FAIL_DIALOG_TITLE );
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
    Status status = host.getStatus( file );
    return host.getConfig().isOffline || (status == Status.NOT_AN_ELEMENT);
  }
}
