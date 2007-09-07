package net.sourceforge.transparent.Annotations;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.text.StringTokenizer;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Sep 5, 2007
 */
public class AnnotationLineParser
{
  private final static String FIELDS_DELIMITER = " | ";

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
    StringTokenizer tokenizer = new StringTokenizer( line + " ", FIELDS_DELIMITER, false );

    //  We rely on the formatter string: "%Sd | %-8.8u | %-16.16Vn | " which
    //  exlicitely delimits date, user and revision number.

    try
    {
      info.date = tokenizer.nextToken();
      info.committer = tokenizer.nextToken();
      info.revision = tokenizer.nextToken();
      info.source = tokenizer.nextToken();
    }
    catch( Exception e )
    {
      throw new VcsException( "Can not parse annotation log" );
    }

    return info;
  }
}
