package net.sourceforge.transparent;

import com.intellij.openapi.util.text.StringUtil;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class CommandLineClearCase implements ClearCase
{
  @NonNls private final static String VERSIONED_SIG = "@@";
  @NonNls private final static String RESERVED_SIG = "reserved";
  @NonNls private final static String UNRESERVED_SIG = "unreserved";
  @NonNls private final static String HIJACKED_SIG = "[hijacked]";
  @NonNls private final static String CHECKEDOUT_SIG = "Rule: CHECKEDOUT";

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
    if( StringUtil.isNotEmpty( comment ) ) {
      cleartool( new String[] {  "co", "-c", quote(comment), isReserved ? "-reserved" : "-unreserved", file.getAbsolutePath() });
    } else {
      cleartool( new String[] {  "co", "-nc", isReserved ? "-reserved" : "-unreserved", file.getAbsolutePath()  });
    }
  }

  public void delete(File file, String comment) {
    if( StringUtil.isNotEmpty( comment ) )
      cleartool( new String[] { "rmname", "-force", "-c", quote(comment), file.getAbsolutePath() } );
    else
      cleartool( new String[] { "rmname", "-force", file.getAbsolutePath() } );
  }

  public void add( File file, String comment )
  {
    doAdd( file.isDirectory() ? "mkdir" : "mkelem", file.getAbsolutePath(), comment );
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
    
    if( !runner.isSuccessfull() )
      throw new ClearCaseException( runner.getOutput() );

    if( runner.getOutput().indexOf( VERSIONED_SIG ) == -1 )
      return Status.NOT_AN_ELEMENT;

    if( runner.getOutput().indexOf( HIJACKED_SIG ) != -1 )
      return Status.HIJACKED;

    if( runner.getOutput().indexOf( CHECKEDOUT_SIG ) != -1 )
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
