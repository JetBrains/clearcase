package net.sourceforge.transparent.Checkin;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.TransparentVcs;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Apr 18, 2007
 */

public class CCaseCheckinHandler extends CheckinHandler
{
  private final TransparentVcs host;
  private final CheckinProjectPanel panel;

  public CCaseCheckinHandler( TransparentVcs host, final CheckinProjectPanel panel )
  {
    this.host = host;
    this.panel = panel;
  }

  public ReturnResult beforeCheckin()
  {
    Collection<VirtualFile> files = panel.getVirtualFiles();
    Set<VirtualFile> set = new HashSet<VirtualFile>();

    //  Add those folders which are renamed and are parents for the files
    //  marked for checkin.
    for( VirtualFile file : files )
    {
      for( String newFolderName : host.renamedFolders.keySet() )
      {
        if( file.getPath().startsWith( newFolderName ) )
        {
          VirtualFile parent = VcsUtil.getVirtualFile( newFolderName );
          set.add( parent );
        }
      }
    }

    //  Remove all folders which are marked for checkin, leave only those
    //  which are absent in the list.
    for( VirtualFile file : files )
      set.remove( file );

    if( set.size() > 0 )
    {
      int result = Messages.showOkCancelDialog( "Renamed folder(s) for committed file(s) will be added to the change list",
                                                "Change list is incomplete", Messages.getWarningIcon() );
      if( result != 0 )
        return ReturnResult.CANCEL;
    }
    return ReturnResult.COMMIT;
  }
}
