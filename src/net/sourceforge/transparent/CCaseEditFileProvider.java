package net.sourceforge.transparent;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Dec 19, 2006
 * Time: 2:26:37 PM
 * To change this template use File | Settings | File Templates.
 */
public class CCaseEditFileProvider implements EditFileProvider
{
  private TransparentVcs host;

  public CCaseEditFileProvider(TransparentVcs host )
  {
    this.host = host;
  }

  public String getRequestText() {  return "Would you like to invoke 'CheckOut' command?";  }

  public void editFiles( VirtualFile[] files )
  {
    ChangeListManager mgr = ChangeListManager.getInstance( host.getProject() );
    for( VirtualFile file : files )
    {
      if( !mgr.isIgnoredFile( file ) )
          checkOutOrHijackFile(file);
    }
  }

  public void checkOutOrHijackFile(VirtualFile file)
  {
      try {
          makeFileWritable(file);
      }
      catch (Throwable e) {
        Messages.showErrorDialog( "Exception while " + (shouldHijackFile(file) ? "hijacking " : "checking out ") + file.getPresentableUrl(),
                                  "message.title.could.not.start.process" );
          /*
          vcsHelper.showErrors(Arrays.asList(new VcsException[] {
              new VcsException(e)
          }), "Exception while " + (shouldHijackFile(file) ? "hijacking " : "checking out ") + file.getPresentableUrl());
          */
      }
  }

  public boolean shouldHijackFile( VirtualFile file )
  {
      return host.getTransparentConfig().offline || !isElement( file);
  }

  private boolean isElement( VirtualFile file )
  {
    ClearCaseFile ccFile = new ClearCaseFile( file, host.getClearCase() );
    return ccFile.isElement();
  }

  private void makeFileWritable(VirtualFile file) throws VcsException
  {
    if( shouldHijackFile( file ) )
        hijackFile( file );
    else
        host.checkoutFile( file.getPresentableUrl(), false );
  }

  public static void hijackFile(VirtualFile file) throws VcsException {
    try {
      ReadOnlyAttributeUtil.setReadOnlyAttribute( file, false );
//          new FileUtil().setFileWritability(file, true);
    }
    catch (Exception e) {
        throw new VcsException(e);
    }
  }
}
