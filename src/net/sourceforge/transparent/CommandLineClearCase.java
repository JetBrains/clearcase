package net.sourceforge.transparent;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public class CommandLineClearCase implements ClearCase
{
  @NonNls private final static String VERSIONED_SIG = "@@";
  @NonNls private final static String RESERVED_SIG = "reserved";
  @NonNls private final static String UNRESERVED_SIG = "unreserved";
  @NonNls private final static String HIJACKED_SIG = "[hijacked]";
  @NonNls private final static String CHECKEDOUT_SIG = "Rule: CHECKEDOUT";

  @NonNls private final static String NOT_VOB_ELEMENT = "Pathname is not within";
  @NonNls private final static String UNABLE_TO_ACCESS = "Unable to access";
  @NonNls private final static String NO_SUCH_FILE_OR_DIR = "No such file or directory";

  public String getName() {
    return (net.sourceforge.transparent.CommandLineClearCase.class).getName();
  }

  public void undoCheckOut( File file ) {
    cleartool( new String[] { "unco", "-rm", file.getAbsolutePath() } );
  }

  public void checkIn( File file, String comment )
  {
    if( StringUtil.isNotEmpty( comment ) )
      cleartool( new String[] { "ci", "-c", quote( comment ), "-identical", file.getAbsolutePath() } );
    else
      cleartool( new String[] { "ci", "-nc", "-identical", file.getAbsolutePath() } );
  }

  public void checkOut( File file, boolean isReserved, String comment )
  {
    if( StringUtil.isNotEmpty( comment ) )
      cleartool( new String[] {  "co", "-c", quote(comment), isReserved ? "-reserved" : "-unreserved", "-nq", file.getAbsolutePath() });
    else
      cleartool( new String[] {  "co", "-nc", isReserved ? "-reserved" : "-unreserved", "-nq", file.getAbsolutePath()  });
  }

  public void delete( File file, String comment)
  {
    if( StringUtil.isNotEmpty( comment ) )
      cleartool( new String[] { "rmname", "-force", "-c", quote(comment), file.getAbsolutePath() } );
    else
      cleartool( new String[] { "rmname", "-force", file.getAbsolutePath() } );
  }

  public void add( File file, String comment )
  {
    if( file.isDirectory() )
      doAddDir( file, comment );
    else
      doAdd( "mkelem", file.getAbsolutePath(), comment );
  }

  /**
   * From ClearCase documentation:
   * ----------------------------
   * Converting View-Private Directories
   * You cannot create a directory element with the same name as an existing
   * view-private file or directory, and you cannot use mkdir to convert an
   * existing view-private directory structure into directory and file elements.
   * To accomplish this task, use clearfsimport.
   */
  private static void doAddDir( File dir, String comment )
  {
    String ext = Long.toString( new Date().getTime() );

    //  Error message if first rename fails.
    @NonNls String prefix = "Could not rename the content of " + dir.getPath();
    @NonNls String error = prefix + " as part of adding it to ClearCase." + " Please add it manually";

    File tmpDir = new File( dir.getParentFile(), dir.getName() + "." + ext );
    try
    {
      FileUtil.rename( dir, tmpDir );
      try
      {
        doAdd( "mkdir", dir.getAbsolutePath(), comment );
        FileUtil.delete( dir );
      }
      finally
      {
        //  Error message if second rename (back) fails.
        error = prefix + " back as part of adding it to Clearcase:\n" + "Its old content is in the " +
                tmpDir.getName() + ". Please rename it back manually.";
        FileUtil.moveDirWithContent( tmpDir, dir );
      }
    }
    catch( IOException e )
    {
      throw new ClearCaseException( error );
    }
  }

  private static void doAdd( @NonNls String subcmd, String path, String comment )
  {
    if( StringUtil.isNotEmpty( comment ) )
      cleartool( new String[] { subcmd, "-c", quote(comment), path } );
    else
      cleartool( new String[] { subcmd, "-nc", path } );
  }

  public void move(File file, File target, String comment)
  {
    cleartool( new String[] { "mv", "-c", quote(comment), file.getAbsolutePath(), target.getAbsolutePath() } );
  }

  public boolean isElement(File file) {  return getStatus(file) != Status.NOT_AN_ELEMENT;  }

  public boolean isCheckedOut(File file) {  return getStatus(file) == Status.CHECKED_OUT;  }

  public Status getStatus(File file)
  {
    String fileName = quote( file.getAbsolutePath() );
    Runner runner = cleartool( new String[] { "ls", "-directory", fileName }, true);
    String output = runner.getOutput();
    if( output == null )
      output = "";

    //  Check message "Pathname is not withing a VOB:..." first because it comes
    //  along with Failure exit code for cleartool command, and we may return
    //  with ClearCaseException without giving useful information. 
    if( output.indexOf( NOT_VOB_ELEMENT ) != -1 )
      return Status.NOT_AN_ELEMENT;

    //  Check message "Unable to access...No such file or directory" first
    //  because it comes along with Failure exit code for cleartool command,
    //  and we may return with ClearCaseException without giving useful information.
    //  NB: I found this message only appearing when working with dynamic views,
    //      potential failure in synching VirtualFile via VFS?
    if( output.indexOf( UNABLE_TO_ACCESS ) != -1 && output.indexOf( NO_SUCH_FILE_OR_DIR ) != -1 )
      return Status.NOT_AN_ELEMENT;

    if( !runner.isSuccessfull() )
      throw new ClearCaseException( output );

    if( output.indexOf( VERSIONED_SIG ) == -1 )
      return Status.NOT_AN_ELEMENT;

    if( output.indexOf( HIJACKED_SIG ) != -1 )
      return Status.HIJACKED;

    if( output.indexOf( CHECKEDOUT_SIG ) != -1 )
      return Status.CHECKED_OUT;
    else
      return Status.CHECKED_IN;
  }

  public void cleartool( @NonNls String subcmd )
  {
    cleartool( new String[] { "cleartool", subcmd } );
  }

  public static void cleartool( @NonNls String[] subcmd ) {
    cleartool(subcmd, false);
  }

  private static Runner cleartool(@NonNls String[] subcmd, boolean canFail)
  {
    @NonNls String[] cmd = Runner.getCommand( "cleartool", subcmd );
    Runner runner = new Runner();
    runner.run(cmd, canFail);
    return runner;
  }

  public CheckedOutStatus getCheckedOutStatus(File file)
  {
    Runner runner = cleartool(new String[] { "lscheckout", "-fmt", "%Rf", "-directory", file.getAbsolutePath() }, true);
    
    if (!runner.isSuccessfull())
      return CheckedOutStatus.NOT_CHECKED_OUT;

    if (runner.getOutput().equalsIgnoreCase( RESERVED_SIG ) )
      return CheckedOutStatus.RESERVED;

    if (runner.getOutput().equalsIgnoreCase( UNRESERVED_SIG ))
      return CheckedOutStatus.UNRESERVED;

    return CheckedOutStatus.NOT_CHECKED_OUT;
    /*
    if (runner.getOutput().equals("")) {
        return CheckedOutStatus.NOT_CHECKED_OUT;
    } else {
        return CheckedOutStatus.NOT_CHECKED_OUT;
    }
    */
  }

  @Nullable
  public String getCheckoutComment(File file) { return null;  }

  public static String quote(String str) {  return "\"" + str.replaceAll("\"", "\\\"") + "\"";  }
}
