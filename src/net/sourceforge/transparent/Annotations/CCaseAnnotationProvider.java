package net.sourceforge.transparent.Annotations;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import net.sourceforge.transparent.CCaseHistoryProvider;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Sep 5, 2007
 */
public class CCaseAnnotationProvider implements AnnotationProvider
{
  @NonNls private final static String BRANCH_SIG = "branch";

  Project project;
  TransparentVcs host;

  public CCaseAnnotationProvider( Project project, TransparentVcs host )
  {
    this.project = project;
    this.host = host;
  }

  public FileAnnotation annotate(VirtualFile file) throws VcsException
  {
    String canonicalName = getCanonicalPath( file.getPath() );
    
    FileStatus status = FileStatusManager.getInstance(project).getStatus( file );
    if( status == FileStatus.HIJACKED )
      canonicalName += "@@";

    return runAnnotation( canonicalName );
  }

  public FileAnnotation annotate( VirtualFile file, VcsFileRevision vcsRev ) throws VcsException
  {
    String canonicalName = getCanonicalPath( file.getPath() );
    canonicalName += vcsRev.getRevisionNumber().asString();

    return runAnnotation( canonicalName );
  }

  private static String getCanonicalPath( final String path )
  {
    String canonicalName;
    try {  canonicalName = new File( path ).getCanonicalPath();  }
    catch( IOException e )
    {
      canonicalName = path;
    }
    return canonicalName;
  }

  private static FileAnnotation runAnnotation( final String path ) throws VcsException
  {
    String output = TransparentVcs.cleartoolWithOutput( "annotate", "-out", "-", "-nco", "-nhe", "-fmt", "\"%Sd | %-16.16u | %-40.40Vn | \"", path );
    CCaseFileAnnotation annotation = new CCaseFileAnnotation();
    String[] lines = LineTokenizer.tokenize( output, false );

    for( String line : lines )
    {
      AnnotationLineParser.AnnotationLineInfo info = AnnotationLineParser.parse( line );
      annotation.addLineInfo( info.date, info.revision, info.committer, info.source );
    }
    return annotation;
  }

  public boolean isAnnotationValid( VcsFileRevision rev )
  {
    CCaseHistoryProvider.CCaseFileRevision ccRev = (CCaseHistoryProvider.CCaseFileRevision) rev;
    return (ccRev.getAction().indexOf( BRANCH_SIG ) == -1);
  }
}
