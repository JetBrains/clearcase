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

  private final String[] files;
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
    return file2Activity.get( fileName );
  }

  /**
   * NB: The strict format (hm, grammar, boys!) of the command output is not
   *     defined since it may contain info, warning and error messages from
   *     different subsystems involved during the command execution.
   * Example: !> cleartool describe <file>
   *          !noname: Warning: Can not find a group named "XXX"
   *          !<file>@@<version>  --> <activity>
   *
   * Thus we can rely only on some patterns which strip out known garbage messages.
   */
  private void parseCleartoolOutput( final String out, int startIndex )
  {
    TransparentVcs.LOG.info( "\n" + out );

    int shiftIndex = 0;
    String[] lines = LineTokenizer.tokenize( out, false );
    for( String line : lines )
    {
      int index = line.indexOf( DELIMITER );
      if( index != -1 )
      {
        String activity = line.substring( index + DELIMITER.length() );
        file2Activity.put( files[ shiftIndex + startIndex ], activity );
        shiftIndex++;
      }
    }
  }
}
