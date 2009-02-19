package net.sourceforge.transparent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
  private final StringBuffer _buffer = new StringBuffer();
  private boolean successfull;
  public String workingDir = null;

  private class Consumer implements Runnable
  {
    private final BufferedReader _reader;

    public Consumer(InputStream inputStream) {
       _reader = new BufferedReader(new InputStreamReader(inputStream));
    }

    public void run() {
      try {
        String line;
        while ((line = _reader.readLine()) != null) {
          if (DEBUG) System.out.println("      " + line);
          if (_buffer.length() != 0) _buffer.append("\n"); // not theadsafe, but who cares
          _buffer.append(line);
        }
      } catch (Exception e) {
        throw new RuntimeException(e.getMessage());
      }
    }
  }

  private Process startProcess( String[] command ) throws IOException, InterruptedException
  {
    String cmdLine = getCommandLine( command );

    Process process;
    if( workingDir == null )
      process = Runtime.getRuntime().exec( command );
    else
    {
      File wrkDir = new File( workingDir );
      if( !wrkDir.exists() || !wrkDir.isDirectory() )
        throw new IOException( "Path " + workingDir + " is not a valid working directory for a command: " + cmdLine );

      process = Runtime.getRuntime().exec( command, null, wrkDir );
    }

    consumeProcessOutputs( process );

    return process;
  }

  public static String getCommandLine(String[] command)
  {
    StringBuffer buf = new StringBuffer();
    for (String aCommand : command) {
      buf.append(aCommand).append(" ");
    }
    return buf.toString();
  }

  private void consumeProcessOutputs(Process process) throws InterruptedException
  {
    Consumer outputConsumer = new Consumer(process.getInputStream());
    Consumer errorConsumer =  new Consumer(process.getErrorStream());
    final Future<?> errorDone = ApplicationManager.getApplication().executeOnPooledThread(errorConsumer);
    outputConsumer.run();
    try {
      errorDone.get();
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
  }

   private static boolean endProcess(Process process) throws InterruptedException {
      return process.waitFor() == 0;
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
         Process process = startProcess(command);

         successfull = endProcess( process );
         if( successfull ){
            return true;
         } else {
            if (!canFail) throw new ClearCaseException("Error executing " + getCommandLine(command) + " : " + _buffer);
            return false;
         }
      } catch (RuntimeException e) {
        if( StringUtil.isNotEmpty( workingDir ) )
          LOG.info( "CCAse runtime exception (started in [" + workingDir + "]: " + e.getMessage(), e );
        else
          LOG.info( "CCAse runtime exception :" + e.getMessage(), e );

        throw e;
      } catch (Exception e) {
        if( StringUtil.isNotEmpty( workingDir ) )
          LOG.info( "CCAse exception: (started in [" + workingDir + "]: " + e.getMessage(), e );
        else
          LOG.info( "CCAse exception: " + e.getMessage(), e );
        throw new RuntimeException(e.getMessage());
      }
   }


   public String getOutput() {  return _buffer.toString();   }

   public boolean isSuccessfull() {  return successfull;   }

   public static String[] getCommand( @NonNls String exec, String[] args)
   {
      String[] cmd = new String[ args.length + 1 ];
      cmd[ 0 ] = exec;
      System.arraycopy(args, 0, cmd, 1, args.length);
      return cmd;
   }
}

