package net.sourceforge.transparent;

import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.ArrayUtil;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class StatusMultipleProcessor
{
  @NonNls private static final String STATUS_COMMAND = "ls";
  @NonNls private static final String DIR_SWITCH = "-directory";
  @NonNls private static final String RECURSE_SWITCH = "-recurse";
  @NonNls private static final String VIEW_ONLY = "-view_only";
  @NonNls private static final String VERSIONED_SIG = "@@";
  @NonNls private static final String HIJACKED_SIG = "[hijacked]";
  @NonNls private static final String CHECKEDOUT_SIG = "Rule: CHECKEDOUT";
  @NonNls private static final String LOCALLY_DELETED = "[loaded but missing]";
  @NonNls private final static String CHECKEDOUT_REMOVED_SIG = "checkedout but removed";

  @NonNls private final static String WARNING_TO_SKIP_SIG = "Warning: "; 

  private static final int  CMDLINE_MAX_LENGTH = 512;

  private final String[] files;
  private boolean myRecursive;
  private boolean myViewOnly;

  private HashSet<String> locallyDeleted;
  private HashSet<String> deletedFiles;
  private HashSet<String> nonexistingFiles;
  private HashSet<String> checkoutFiles;
  private HashSet<String> hijackedFiles;

  public StatusMultipleProcessor( List<String> paths )
  {
    files = ArrayUtil.toStringArray(paths);
  }

  public HashSet<String> getLocallyDeleted() {
    return locallyDeleted;
  }

  public HashSet<String> getUnversioned() {
    return nonexistingFiles;
  }

  public HashSet<String> getCheckoutFiles() {
    return checkoutFiles;
  }

  public HashSet<String> getHijackedFiles() {
    return hijackedFiles;
  }

  @Nullable
  public static String getCurrentRevision(final String path) {
    final String out = TransparentVcs.cleartoolWithOutput(STATUS_COMMAND, DIR_SWITCH, path);
    if (out.contains(WARNING_TO_SKIP_SIG)) return null;
    final int idxVer = out.indexOf(VERSIONED_SIG);
    if (idxVer == -1) return null;
    return new String(out.substring(idxVer, out.indexOf(' ', idxVer)));
  }

  public void execute()
  {
    deletedFiles = new HashSet<>();
    nonexistingFiles = new HashSet<>();
    checkoutFiles = new HashSet<>();
    hijackedFiles = new HashSet<>();
    locallyDeleted = new HashSet<>();

    int currFileIndex = 0;
    int batchStartIndex = 0;
    int cmdLineLen;
    LinkedList<String> options = new LinkedList<>();
    while( currFileIndex < files.length )
    {
      cmdLineLen = 0;
      options.clear();

      options.add(STATUS_COMMAND);
      cmdLineLen += STATUS_COMMAND.length() + 1;
      if (myRecursive) {
        options.add(RECURSE_SWITCH);
        cmdLineLen += RECURSE_SWITCH.length() + 1;
      } else {
        options.add(DIR_SWITCH);
        cmdLineLen += DIR_SWITCH.length() + 1;
      }
      if (myViewOnly) {
        options.add(VIEW_ONLY);
        cmdLineLen += VIEW_ONLY.length() + 1;
      }

      while( currFileIndex < files.length && cmdLineLen < CMDLINE_MAX_LENGTH )
      {
        String path = files[ currFileIndex++ ];
        options.add( path );
        cmdLineLen += path.length() + 1;
      }

      String[] aOptions = ArrayUtil.toStringArray(options);
      String out = TransparentVcs.cleartoolWithOutput( aOptions );
      try
      {
        parseCleartoolOutput( out, batchStartIndex );
      }
      catch( Exception e )
      {
        TransparentVcs.LOG.info( "Failed to parse LS output (possible unknown message format):" );
        TransparentVcs.LOG.info( out );
        throw new ClearCaseException( "Failed to parse LS output (possible unknown message format):" + e.getMessage() );
      }
      batchStartIndex = currFileIndex;
    }
  }

  /**
   * NB: The strict format (hm, grammar, boys!) of the command output is not
   *     defined since it may contain info, warning and error messages from
   *     different subsystems involved during the command execution.
   * Example: !> cleartool ls <file>
   *          !noname: Warning: Can not find a group named "XXX"
   *          !<file>@@<version>  etc...
   *
   * Thus we can rely only on some patterns which strip out known garbage messages.
   */
  private void parseCleartoolOutput( final String out, int startIndex )
  {
    int shiftIndex = 0;
    String[] lines = LineTokenizer.tokenize( out, false );

    for( String line : lines )
    {
      if( line.indexOf( WARNING_TO_SKIP_SIG ) == -1 )
      {
        final int versIdx = line.indexOf(VERSIONED_SIG);
        if( versIdx == -1) {
          nonexistingFiles.add(line.replace('\\', '/'));
        } else if( line.indexOf( CHECKEDOUT_SIG ) != -1) {
          checkoutFiles.add(filePathFromLine(line, versIdx));
          // todo verify what below
        } else if (line.indexOf(LOCALLY_DELETED) != -1 || line.indexOf( CHECKEDOUT_REMOVED_SIG ) != -1) {
          locallyDeleted.add(filePathFromLine(line, versIdx));
        } else if( line.indexOf( HIJACKED_SIG ) != -1 )
          hijackedFiles.add(filePathFromLine(line, versIdx));

        //  inc it only in the case of "known" line format. Assume that information
        //  about files is printed in the order of input parameters (silly?).
        shiftIndex++;
      }
    }
  }

  private String filePathFromLine(String line, int versIdx) {
    return line.substring(0, versIdx).replace('\\', '/');
  }

  public void setRecursive(boolean recursive) {
    myRecursive = recursive;
  }

  public void setViewOnly(boolean viewOnly) {
    myViewOnly = viewOnly;
  }
}
