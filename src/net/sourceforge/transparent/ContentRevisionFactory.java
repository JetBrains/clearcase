package net.sourceforge.transparent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: May 14, 2007
 */
public class ContentRevisionFactory
{
  private static HashMap<FilePath, CCaseContentRevision> cachedRevisions;

  static
  {
    cachedRevisions = new HashMap<FilePath, CCaseContentRevision>();
  }

  private ContentRevisionFactory() {}

  public static CCaseContentRevision getRevision( @NotNull FilePath path, Project project )
  {
    CCaseContentRevision revision = cachedRevisions.get( path );
    if( revision == null )
    {
      revision = new CCaseContentRevision( path, project );
      cachedRevisions.put( path, revision );
    }
    return revision;
  }

  public static void clearCacheForFile( String file )
  {
    FilePath path = VcsUtil.getFilePath( file );
    cachedRevisions.remove( path );
  }
}
