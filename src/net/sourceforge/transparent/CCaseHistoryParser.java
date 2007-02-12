package net.sourceforge.transparent;

import com.intellij.openapi.util.text.LineTokenizer;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Jan 26, 2007
 */
public class CCaseHistoryParser
{
  @NonNls public static final String BRANCH_COMMAND_SIG = "create branch";
  @NonNls public static final String CREATE_ELEM_COMMAND_SIG = "create file element";

  @NonNls private static final String DATE_DELIM = "   ";
  @NonNls private static final String COMMENT_SIG = "  \"";
  @NonNls private static final String ERROR_SIG = "History parsing error";
  @NonNls private static String[] actions = { CREATE_ELEM_COMMAND_SIG, BRANCH_COMMAND_SIG, "create version",
                                              /*"checkout version", */"checkin version"};

  private CCaseHistoryParser() {}

  public static class SubmissionData
  {
    public String action;
    public String version;
    public String submitter;
    public String changeDate;
    public String changeTime;
    public String comment;
    public String labels;
    public int    order;
  }
  
  public static ArrayList<SubmissionData> parse( final String content )
  {
    int order = 0;
    ArrayList<SubmissionData> changes = new ArrayList<SubmissionData>();
    String[] lines = LineTokenizer.tokenize( content, false );
    for( String line : lines )
    {
      SubmissionData change = new SubmissionData();
      change.order = order++;

      //  For every comment line - concatenate it with the comment
      //  of the last recorder change.
      if( line.startsWith( COMMENT_SIG ) )
      {
        if( changes.size() > 0 )
        {
          SubmissionData lastChange = changes.get( changes.size() - 1 );
          String newComment = line.substring( COMMENT_SIG.length(), line.length() - 2 );
          String comment = lastChange.comment;
          lastChange.comment = (comment == null) ? newComment : comment.concat( newComment );
        }
      }
      else
      {
        for ( String action : actions )
        {
          int index = line.indexOf( action );
          if ( index != -1 )
          {
            change.action = action;

            try
            {
              parseLeftSide( change, line.substring( 0, index - 1 ));
              parseRightSide( change, line.substring( index + action.length() ));
            }
            catch( Exception e )
            {
              //  Potentially - not enough knowledge on history format,
              //  construct a dummy change with the source line as a comment
              //  for future diagnostics.
              change.comment = line;
              change.action = ERROR_SIG;
            }
            
            changes.add( change );
            break;
          }
        }
      }
    }
    return changes;
  }

  private static void parseLeftSide( SubmissionData data, String str )
  {
    int index = str.indexOf( DATE_DELIM );
    parseDateTime( str.substring( 0, index ), data );
    data.submitter = str.substring( index + DATE_DELIM.length() ).trim();
  }

  private static void parseRightSide( SubmissionData data, String str )
  {
    int index = str.indexOf( "@@" );
    int finalIndex = str.indexOf( '"', index + 1 );
    data.version = str.substring( index, finalIndex );

    //  parse optional labels
    index = str.indexOf( '(', finalIndex );
    if( index != -1 )
    {
      finalIndex = str.indexOf( ')', index + 1 );
      data.labels = str.substring( index + 1, finalIndex ); 
    }
  }

  //  CCase's date/time format is given without year fiels, catenated via '.' -
  //  22-Dec.15:14
  private static void parseDateTime( String str, SubmissionData data )
  {
    int index = str.indexOf( '.' );
    if( index != -1 )
    {
      data.changeDate = str.substring( 0, index - 1 );
      data.changeTime = str.substring( index + 1 );
    }
  }
}
