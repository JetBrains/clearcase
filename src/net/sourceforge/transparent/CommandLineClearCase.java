package net.sourceforge.transparent;

import com.intellij.openapi.util.text.StringUtil;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.intellij.plugins.util.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class CommandLineClearCase implements ClearCase
{
  @NonNls private final static String ADD_EXT = ".add";

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
      cleartool( new String[] { "ci", "-identical", file.getAbsolutePath() } );
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

  public void add(File file, String comment) {
    if (file.isDirectory()) {
        doAddDir( file, comment );
    } else {
        doAdd( "mkelem", file.getAbsolutePath(), comment );
    }
  }

  private static void doAddDir( File dir, String comment )
  {
    File tmpDir = new File( dir.getParentFile(), dir.getName() + ADD_EXT );
    if (!dir.renameTo(tmpDir)) {
        throw new ClearCaseException("Could not rename " + dir.getPath() + " to " + tmpDir.getName());
    }
    try {
        doAdd("mkdir", dir.getAbsolutePath(), comment);
    }
    finally {
        if (!FileUtil.moveDirWithContent(tmpDir, dir)) {
            throw new ClearCaseException(
                    "Could not move back the content of " + dir.getPath()
                            + " as part of adding it to Clearcase:\n"
                            + "Its old content is in " + tmpDir.getName()
                            + ". Please move it back manually");
        }
    }
  }

  private static void doAdd( @NonNls String subcmd, String path, String comment ) {
    cleartool(new String[] {  subcmd, "-c", quote(comment), path  });
  }

  public void move(File file, File target, String comment) {
    cleartool( new String[] { "mv", "-c", quote(comment), file.getAbsolutePath(), target.getAbsolutePath() } );
  }

  public boolean isElement(File file) {  return getStatus(file) != Status.NOT_AN_ELEMENT;  }

  public boolean isCheckedOut(File file) {  return getStatus(file) == Status.CHECKED_OUT;  }

  public Status getStatus(File file)
  {
    String fileName = quote( file.getAbsolutePath() );
    Runner runner = cleartool( new String[] { "ls", "-directory", fileName }, true);
    
    if (!runner.isSuccessfull()) {
      throw new ClearCaseException( runner.getOutput() );
    }
    if (runner.getOutput().indexOf("@@") == -1) {
        return Status.NOT_AN_ELEMENT;
    }
    if (runner.getOutput().indexOf("[hijacked]") != -1) {
        return Status.HIJACKED;
    }
    if (runner.getOutput().indexOf("Rule: CHECKEDOUT") != -1) {
        return Status.CHECKED_OUT;
    } else {
        return Status.CHECKED_IN;
    }
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
    @NonNls String[] cmd = Runner.getCommand("cleartool", subcmd);
    Runner runner = new Runner();
    runner.run(cmd, canFail);
    return runner;
  }

  public CheckedOutStatus getCheckedOutStatus(File file)
  {
    Runner runner = cleartool(new String[] { "lscheckout", "-fmt", "%Rf", "-directory", file.getAbsolutePath() }, true);
    
    if (!runner.isSuccessfull()) {
        return CheckedOutStatus.NOT_CHECKED_OUT;
    }
    if (runner.getOutput().equalsIgnoreCase("reserved")) {
        return CheckedOutStatus.RESERVED;
    }
    if (runner.getOutput().equalsIgnoreCase("unreserved")) {
        return CheckedOutStatus.UNRESERVED;
    }
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
