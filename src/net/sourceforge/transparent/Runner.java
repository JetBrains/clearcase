package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.process.ProcessCloseUtil;
import com.intellij.openapi.util.text.StringUtil;
import net.sourceforge.transparent.exceptions.ClearCaseException;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Runner
{
  private static final Logger LOG = Logger.getInstance("#net.sourceforge.transparent.Runner");

  private static final boolean DEBUG = false;
  private boolean successfull;
  private Process myProcess;
  public String workingDir = null;
  private String myOutput;

  private static class Consumer implements Runnable
  {
    private final StringBuilder _buffer = new StringBuilder();
    private final BufferedReader _reader;

    public Consumer(InputStream inputStream) {
       _reader = new BufferedReader(new InputStreamReader(inputStream));
    }

    public void run() {
      try {
        String line;
        while ((line = _reader.readLine()) != null) {
          if (DEBUG) System.out.println("      " + line);
          if (_buffer.length() != 0) _buffer.append("\n");
          _buffer.append(line);
        }
      } catch (Exception e) {
        throw new RuntimeException(e.getMessage());
      }
    }

    public StringBuilder get_buffer() {
      return _buffer;
    }
  }

  private void startProcess( String[] command ) throws IOException, InterruptedException
  {
    String cmdLine = getCommandLine( command );

    if( workingDir == null )
      myProcess = Runtime.getRuntime().exec( command );
    else
    {
      File wrkDir = new File( workingDir );
      if( !wrkDir.exists() || !wrkDir.isDirectory() )
        throw new IOException( "Path " + workingDir + " is not a valid working directory for a command: " + cmdLine );

      myProcess = Runtime.getRuntime().exec( command, null, wrkDir );
    }

    consumeProcessOutputs();
  }

  public static String getCommandLine(String[] command)
  {
    StringBuffer buf = new StringBuffer();
    for (String aCommand : command) {
      buf.append(aCommand).append(" ");
    }
    return buf.toString();
  }

  private void consumeProcessOutputs() throws InterruptedException
  {
    Consumer outputConsumer = new Consumer(myProcess.getInputStream());
    Consumer errorConsumer =  new Consumer(myProcess.getErrorStream());
    final Future<?> errorDone = ApplicationManager.getApplication().executeOnPooledThread(errorConsumer);
    outputConsumer.run();
    try {
      errorDone.get();
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
    final StringBuilder out = outputConsumer.get_buffer();
    final StringBuilder error = errorConsumer.get_buffer();
    myOutput = out + (error.length() > 0 ? ((out.length() > 0 ? "\n" : "") + error) : "");
  }

   private boolean endProcess() throws InterruptedException {
      return myProcess.waitFor() == 0;
   }

   public static void runAsynchronously(String command) throws IOException {
      Runtime.getRuntime().exec( command );
   }

  public static void runAsynchronously(String[] command) throws IOException {
     Runtime.getRuntime().exec( command );
  }

  public static void runAsynchronouslyOnPath( String path, String[] command) throws IOException {
     Runtime.getRuntime().exec( command, null, new File( path ) );
  }

   public void run( String   command ) {  run( command, false );   }
   public void run( String[] command ) {  run( command, false );   }

   public boolean run( String command, boolean canFail )
   {
     return run( new String[] { command }, canFail );
   }

   public boolean run( String[] command, boolean canFail )
   {
     LOG.info( "|" + getCommandLine( command ) );
     
      try
      {
        dealWithProcess(command);
         if( successfull ){
            return true;
         } else {
            if (!canFail) throw new ClearCaseException("Error executing " + getCommandLine(command) + " : " + myOutput);
            return false;
         }
      } catch (RuntimeException e) {
        if( StringUtil.isNotEmpty( workingDir ) )
          LOG.info( "CCAse runtime exception (started in [" + workingDir + "]: " + e.getMessage(), e );
        else
          LOG.info( "CCAse runtime exception :" + e.getMessage(), e );
        // this exception will be caught
        throw new ClearCaseException(e.getMessage());
      } catch (Exception e) {
        if( StringUtil.isNotEmpty( workingDir ) )
          LOG.info( "CCAse exception: (started in [" + workingDir + "]: " + e.getMessage(), e );
        else
          LOG.info( "CCAse exception: " + e.getMessage(), e );
        // this exception will be caught
        throw new ClearCaseException(e.getMessage());
      }
   }

  private void dealWithProcess(String[] command) throws IOException, InterruptedException {
    try {
      startProcess(command);
      successfull = endProcess();
    } finally {
      if (myProcess != null) {
        ProcessCloseUtil.close(myProcess);
      }
    }
  }


  public String getOutput() {  return myOutput;   }

   public boolean isSuccessfull() {  return successfull;   }

   public static String[] getCommand( @NonNls String exec, String[] args)
   {
      String[] cmd = new String[ args.length + 1 ];
      cmd[ 0 ] = exec;
      System.arraycopy(args, 0, cmd, 1, args.length);
      return cmd;
   }
}

