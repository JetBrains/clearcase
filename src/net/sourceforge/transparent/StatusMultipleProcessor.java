package net.sourceforge.transparent;

import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
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

  private static final int  CMDLINE_MAX_LENGTH = 500;

  private VirtualFile[] files;

  private HashSet<String> deletedFiles;
  private HashSet<String> nonexistingFiles;
  private HashSet<String> checkoutFiles;
  private HashSet<String> hijackedFiles;

  public StatusMultipleProcessor( List<String> paths )
  {
    files = new VirtualFile[ paths.size() ];
    for( int i = 0; i < paths.size(); i++ )
    {
      VirtualFile file = VcsUtil.getVirtualFile( paths.get( i ) );
      files[ i ] = file;
    }
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
    int cmdLineLen;
    LinkedList<String> options = new LinkedList<String>();
    while( currFileIndex < files.length )
    {
      cmdLineLen = STATUS_COMMAND.length() + DIR_SWITCH.length();

      options.clear();
      options.add( STATUS_COMMAND );

      while( currFileIndex < files.length && cmdLineLen < CMDLINE_MAX_LENGTH )
      {
        String vssPath = files[ currFileIndex++ ].getPath();
        options.add( vssPath );
        cmdLineLen += vssPath.length() + 1;
      }

      String[] aOptions = options.toArray( new String[ options.size() ]);
      String out = TransparentVcs.cleartoolWithOutput( aOptions );
      parseCleartoolOutput( out );
    }
  }

  private void parseCleartoolOutput( final String out )
  {
    String[] lines = LineTokenizer.tokenize( out, false );
    for( int i = 0; i < lines.length; i++ )
    {
      if( lines[ i ].indexOf( VERSIONED_SIG ) == -1 )
        nonexistingFiles.add( files[ i ].getPath() );
      else
      if( lines[ i ].indexOf( CHECKEDOUT_SIG ) != -1 )
        checkoutFiles.add( files[ i ].getPath() );
      else
      if( lines[ i ].indexOf( HIJACKED_SIG ) != -1 )
        hijackedFiles.add( files[ i ].getPath() );
    }
  }
}
