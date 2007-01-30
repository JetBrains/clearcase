/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package net.sourceforge.transparent;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Dec 6, 2006
 * Time: 4:20:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class CCaseUpdateEnvironment implements UpdateEnvironment
{
  @NonNls private final static String LOADING_SIG = "Loading \"";
  @NonNls private final static String KEEP_HIJACKED_SIG = "Keeping hijacked object \"";
  @NonNls private final static String UNLOADED_SIG = "Unloaded \"";
  @NonNls private final static String BASE_DELIM = " - base ";

  private Project project;

  public CCaseUpdateEnvironment( Project project )
  {
    this.project = project;
  }

  public void fillGroups( UpdatedFiles groups )
  {
    final FileGroup groupModified = new FileGroup(/*VssBundle.message("update.group.name.modified"),
                                                  VssBundle.message("update.group.name.modified"), */
                                                  "modified", "modified", false, FileGroup.MODIFIED_ID, false);
    groups.registerGroup(groupModified);

    final FileGroup groupSkipped = new FileGroup(/*VssBundle.message("update.group.name.skipped"),
                                                 VssBundle.message("update.group.name.skipped"), */
                                                 "Skipped", "Skipped", false, FileGroup.SKIPPED_ID, false);
    groups.registerGroup(groupSkipped);
  }

  public UpdateSession updateDirectories( FilePath[] contentRoots, UpdatedFiles updatedFiles, ProgressIndicator progressIndicator ) throws
                                                                                                                                    ProcessCanceledException {
    final ArrayList<VcsException> errors = new ArrayList<VcsException>();

    progressIndicator.setText( "Synching with repository"/*VssBundle.message("message.synch.with.repository")*/ );
    FileDocumentManager.getInstance().saveAllDocuments();

    TransparentConfiguration config = TransparentConfiguration.getInstance( project );
    String out = TransparentVcs.cleartoolWithOutput( "update", config.clearcaseRoot );
    parseOutput( out, updatedFiles );

    return new UpdateSession(){
      public List<VcsException> getExceptions() { return errors; }
      public void onRefreshFilesCompleted()     {}
      public boolean isCanceled()               { return false;  }
    };
  }

  private static void parseOutput( String output, UpdatedFiles updatedFiles )
  {
    String[] lines = LineTokenizer.tokenize( output.toCharArray(), false, true );

    for( String line : lines )
    {
      if( line.startsWith( LOADING_SIG ) )
      {
        int lastQuote = line.lastIndexOf( "\"" );
        String fileName = line.substring( LOADING_SIG.length(), lastQuote );

        // ToDo: need to convert to proper file name here
        updatedFiles.getGroupById( FileGroup.UPDATED_ID ).add( fileName );
      }
      else
      if( line.startsWith( KEEP_HIJACKED_SIG ))
      {
        int lastQuote = line.lastIndexOf( BASE_DELIM );
        String fileName = line.substring( KEEP_HIJACKED_SIG.length(), lastQuote );

        // ToDo: need to convert to proper file name here
        updatedFiles.getGroupById( FileGroup.SKIPPED_ID ).add( fileName );
      }
      else
      if( line.startsWith( UNLOADED_SIG ) )
      {
        String fileName = line.substring( UNLOADED_SIG.length(), line.length() - 2 );

        // ToDo: need to convert to proper file name here
        updatedFiles.getGroupById( FileGroup.REMOVED_FROM_REPOSITORY_ID ).add( fileName );
      }
    }
  }

  @Nullable
  public Configurable createConfigurable( Collection<FilePath> files )
  {
    return null;
  }
}