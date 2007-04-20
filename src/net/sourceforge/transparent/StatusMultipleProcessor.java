package net.sourceforge.transparent;

import com.intellij.openapi.util.text.LineTokenizer;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Feb 22, 2007
 */
public class StatusMultipleProcessor
{
  @NonNls private static final String STATUS_COMMAND = "ls";
  @NonNls private static final String DIR_SWITCH = "-directory";
  @NonNls private static final String VERSIONED_SIG = "@@";
  @NonNls private static final String HIJACKED_SIG = "[hijacked]";
  @NonNls private static final String CHECKEDOUT_SIG = "Rule: CHECKEDOUT";
  @NonNls private final static String CHECKEDOUT_REMOVED_SIG = "checkedout but removed";

  private static final int  CMDLINE_MAX_LENGTH = 500;

  private String[] files;

  private HashSet<String> deletedFiles;
  private HashSet<String> nonexistingFiles;
  private HashSet<String> checkoutFiles;
  private HashSet<String> hijackedFiles;

  public StatusMultipleProcessor( List<String> paths )
  {
    files = paths.toArray( new String[ paths.size() ] );
  }

  public boolean isDeleted( String file ) {
    return deletedFiles.contains( file.toLowerCase() );
  }

  public boolean isNonexist( String file ) {
    return nonexistingFiles.contains( file.toLowerCase() );
  }

  public boolean isCheckedout( String file ) {
    return checkoutFiles.contains( file.toLowerCase() );
  }

  public boolean isHijacked( String file ) {
    return hijackedFiles.contains( file.toLowerCase() );
  }

  public void execute()
  {
    deletedFiles = new HashSet<String>();
    nonexistingFiles = new HashSet<String>();
    checkoutFiles = new HashSet<String>();
    hijackedFiles = new HashSet<String>();

    int currFileIndex = 0;
    int batchStartIndex = 0;
    int cmdLineLen;
    LinkedList<String> options = new LinkedList<String>();
    while( currFileIndex < files.length )
    {
      cmdLineLen = STATUS_COMMAND.length() + DIR_SWITCH.length();

      options.clear();
      options.add( STATUS_COMMAND );

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

  private void parseCleartoolOutput( final String out, int startIndex )
  {
    String[] lines = LineTokenizer.tokenize( out, false );
    for( int i = 0; i < lines.length; i++ )
    {
      if( lines[ i ].indexOf( VERSIONED_SIG ) == -1 )
        nonexistingFiles.add( files[ startIndex + i ].toLowerCase() );
      else
      if( lines[ i ].indexOf( CHECKEDOUT_SIG ) != -1 ||
          lines[ i ].indexOf( CHECKEDOUT_REMOVED_SIG ) != -1 )
        checkoutFiles.add( files[ startIndex + i ].toLowerCase() );
      else
      if( lines[ i ].indexOf( HIJACKED_SIG ) != -1 )
        hijackedFiles.add( files[ startIndex + i ].toLowerCase() );
    }
  }
}
