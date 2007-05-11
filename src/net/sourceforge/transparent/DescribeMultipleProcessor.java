package net.sourceforge.transparent;

import com.intellij.openapi.util.text.LineTokenizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: May 7, 2007
 */
public class DescribeMultipleProcessor
{
  @NonNls private static final String DESCRIBE_COMMAND = "describe";
  @NonNls private static final String FMT_SWITCH = "-fmt";
  @NonNls private static final String FORMAT_SIG = "%Xn --> %[activity]p\n";
  @NonNls private static final String DELIMITER = " --> ";

  private static final int  CMDLINE_MAX_LENGTH = 500;

  private String[] files;
  private HashMap<String, String> file2Activity;

  public DescribeMultipleProcessor( List<String> paths )
  {
    files = paths.toArray( new String[ paths.size() ] );
  }

  public void execute()
  {
    file2Activity = new HashMap<String, String>();

    int currFileIndex = 0;
    int batchStartIndex = 0;
    int cmdLineLen;
    LinkedList<String> options = new LinkedList<String>();
    while( currFileIndex < files.length )
    {
      cmdLineLen = DESCRIBE_COMMAND.length() + FMT_SWITCH.length() + FORMAT_SIG.length();

      options.clear();
      options.add( DESCRIBE_COMMAND );
      options.add( FMT_SWITCH );
      options.add( FORMAT_SIG );

      while( currFileIndex < files.length && cmdLineLen < CMDLINE_MAX_LENGTH )
      {
        String path = files[ currFileIndex++ ];
        options.add( path );
        cmdLineLen += path.length() + 1;
      }

      String[] aOptions = options.toArray( new String[ options.size() ]);
      String out = TransparentVcs.cleartoolWithOutput( aOptions );
      parseCleartoolOutput( out, batchStartIndex );
      batchStartIndex = currFileIndex;
    }
  }

  @Nullable
  public String getActivity( String fileName )
  {
//    String normName = VcsUtil.getCanonicalLocalPath( fileName ).toLowerCase();
//    return file2Activity.get( normName );
    return file2Activity.get( fileName );
  }

  private void parseCleartoolOutput( final String out, int startIndex )
  {
    TransparentVcs.LOG.info( "\n" + out );
    String[] lines = LineTokenizer.tokenize( out, false );
    for( int i = 0; i < lines.length; i++ )
    {
      int index = lines[ i ].indexOf( DELIMITER );
      if( index != -1 )
      {
        String activity = lines[ i ].substring( index + DELIMITER.length() );
        file2Activity.put( files[ i + startIndex ], activity);
      }
    }
  }
}
