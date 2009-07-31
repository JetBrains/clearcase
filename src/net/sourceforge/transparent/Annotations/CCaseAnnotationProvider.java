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
import com.intellij.vcsUtil.VcsUtil;
import net.sourceforge.transparent.History.CCaseHistoryProvider;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Sep 5, 2007
 */
public class CCaseAnnotationProvider implements AnnotationProvider
{
  @NonNls private final static String BRANCH_SIG = "branch";
  @NonNls private final static String ERROR_SIG = "Invalid manager operation";
  @NonNls private final static String ERROR_TEXT = "Probably type manager does not contain Annotate method for this file type";

  Project project;
  TransparentVcs host;

  public CCaseAnnotationProvider( Project project, TransparentVcs host )
  {
    this.project = project;
    this.host = host;
  }

  public FileAnnotation annotate( VirtualFile file ) throws VcsException
  {
    String canonicalName = VcsUtil.getCanonicalPath( file.getPath() );
    
    FileStatus status = FileStatusManager.getInstance(project).getStatus( file );
    if( status == FileStatus.HIJACKED )
      canonicalName += "@@";

    return runAnnotation(file, canonicalName );
  }

  public FileAnnotation annotate( VirtualFile file, VcsFileRevision vcsRev ) throws VcsException
  {
    String canonicalName = VcsUtil.getCanonicalPath( file.getPath() );
    canonicalName += vcsRev.getRevisionNumber().asString();

    return runAnnotation(file, canonicalName );
  }

  private static FileAnnotation runAnnotation(final VirtualFile file, final String path ) throws VcsException
  {
    @NonNls String format = "\"%Sd" + AnnotationLineParser.FIELDS_DELIMITER +
                            "%-16.16u" + AnnotationLineParser.FIELDS_DELIMITER +
                            "%-40.40Vn" + AnnotationLineParser.FIELDS_DELIMITER + "\"";
    String output = TransparentVcs.cleartoolWithOutput( "annotate", "-out", "-", "-nco", "-nhe", "-fmt", format, path );

    //  Show more or less descriptive message for this CCase error. 
    if( output.contains( ERROR_SIG ) )
      throw new VcsException( ERROR_TEXT );
    
    CCaseFileAnnotation annotation = new CCaseFileAnnotation(file);
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
    final String action = ccRev.getAction();
    return (action != null) && (action.indexOf( BRANCH_SIG ) == -1);
  }
}
