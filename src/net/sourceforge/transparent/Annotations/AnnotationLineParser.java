package net.sourceforge.transparent.Annotations;

import com.intellij.openapi.vcs.VcsException;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Sep 5, 2007
 */
public class AnnotationLineParser
{
  public final static String FIELDS_DELIMITER = " #|# ";
  public final static String FIELDS_DELIMITER_RE = " #\\|# ";
  private static final AnnotationLineInfo _cachedValues = new AnnotationLineInfo();

  private AnnotationLineParser() {}

  public static class AnnotationLineInfo
  {
    public String duration;
    public String committer;
    public String date;
    public String revision;
    public String source;
  }

  public static AnnotationLineInfo parse( final String line ) throws VcsException
  {
    AnnotationLineInfo info = new AnnotationLineInfo();
    String[] tokens = line.split( FIELDS_DELIMITER_RE );

    //  We rely on the formatter string: "%Sd | %-16.16u | %-40.40Vn | " which
    //  exlicitely delimits date, user and revision number.

    try
    {
      if( isValueableToken( tokens[ 0 ] ) )
        _cachedValues.date = info.date = tokens[ 0 ].trim();
      else
        info.date = _cachedValues.date;

      if( isValueableToken( tokens[ 1 ] ) )
        _cachedValues.committer = info.committer = tokens[ 1 ].trim();
      else
        info.committer = _cachedValues.committer;

      if( isValueableToken( tokens[ 2 ] ) )
        _cachedValues.revision = info.revision = tokens[ 2 ].trim();
      else
        info.revision = _cachedValues.revision;

      //  Source line may be empty.
      info.source = (tokens.length > 3) ? tokens[ 3 ] : "";
    }
    catch( Exception e )
    {
      throw new VcsException( "Can not parse annotation log: " + line );
    }

    return info;
  }

  private static boolean isValueableToken( final String token )
  {
    String trimmed = token.trim();
    return (trimmed.length() > 0) && !trimmed.equals( "." );
  }
}
