/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package net.sourceforge.transparent;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.update.*;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Dec 6, 2006
 */
public class CCaseUpdateEnvironment implements UpdateEnvironment
{
  @NonNls private final static String LOADING_SIG = "Loading \"";
  @NonNls private final static String KEEP_HIJACKED_SIG = "Keeping hijacked object \"";
  @NonNls private final static String UNLOADED_SIG = "Unloaded \"";
  @NonNls private final static String BASE_DELIM = " - base ";
  @NonNls private final static String VIEW_BASE_PATH_SIG = "Log has been written to";
  @NonNls private final static String UPDATE_FILE_PREFIX_SIG = "update.";
  @NonNls private final static String PROGRESS_TEXT = "Synching with repository";

  @NonNls private final static String ERROR_MSG_SIG = "valid snapshot view path";

  public void fillGroups( UpdatedFiles groups ) {}

  @NotNull
  public UpdateSession updateDirectories(@NotNull FilePath[] contentRoots, UpdatedFiles updatedFiles, ProgressIndicator progressIndicator,
                                         @NotNull final Ref<SequentialUpdatesContext> context) throws ProcessCanceledException
  {
    final ArrayList<VcsException> errors = new ArrayList<VcsException>();

    progressIndicator.setText( PROGRESS_TEXT );
    FileDocumentManager.getInstance().saveAllDocuments();

    for( FilePath root : contentRoots )
    {
      String out = TransparentVcs.cleartoolWithOutput( "update", "-force", root.getPath() );

      //  Correctly process the case when "Update Project" is done over the
      //  dynamic view (only snapshot views can handle this operation).
      if( out.indexOf( ERROR_MSG_SIG ) != -1 )
        errors.add( new VcsException( "You can not update a dynamic view: " + out ) );
      else
        parseOutput( root.getPath(), out, updatedFiles );
    }

    return new UpdateSession(){
      @NotNull
      public List<VcsException> getExceptions() { return errors; }
      public void onRefreshFilesCompleted()     {}
      public boolean isCanceled()               { return false;  }
    };
  }

  private static void parseOutput( String contentRoot, String output, UpdatedFiles updatedFiles )
  {
    String sepSymbol = new String( new char[] { File.separatorChar } );
    HashSet<String> updated = new HashSet<String>();
    HashSet<String> skipped = new HashSet<String>();
    HashSet<String> deleted = new HashSet<String>();

    String rootPath = contentRoot;
    if( !rootPath.endsWith( sepSymbol ) )
      rootPath += sepSymbol;

    String[] lines = LineTokenizer.tokenize( output.toCharArray(), false, true );
    for( String line : lines )
    {
      if( line.startsWith( LOADING_SIG ) )
      {
        int lastQuote = line.lastIndexOf( "\"" );
        String fileName = line.substring( LOADING_SIG.length(), lastQuote );
        updated.add( VcsUtil.getCanonicalLocalPath( fileName ) );
      }
      else
      if( line.startsWith( KEEP_HIJACKED_SIG ))
      {
        int lastQuote = line.lastIndexOf( BASE_DELIM );
        String fileName = line.substring( KEEP_HIJACKED_SIG.length(), lastQuote - 1 );
        skipped.add( VcsUtil.getCanonicalLocalPath( fileName ) );
      }
      else
      if( line.startsWith( UNLOADED_SIG ) )
      {
        String fileName = line.substring( UNLOADED_SIG.length(), line.length() - 2 );
        deleted.add( VcsUtil.getCanonicalLocalPath( fileName ) );
      }
      else
      if( line.startsWith( VIEW_BASE_PATH_SIG ) )
      {
        int updateFileStart = line.lastIndexOf( UPDATE_FILE_PREFIX_SIG );
        if( updateFileStart != -1 )
        {
          line = line.substring( 0, updateFileStart );
          rootPath = line.substring( VIEW_BASE_PATH_SIG.length() + 2 );
        }
      }
    }

    final VcsKey vcsKey = TransparentVcs.getKey();
    for( String path : updated )
      updatedFiles.getGroupById( FileGroup.UPDATED_ID ).add(rootPath + path, vcsKey, null);
    for( String path : skipped )
      updatedFiles.getGroupById( FileGroup.SKIPPED_ID ).add(rootPath + path, vcsKey, null);
    for( String path : deleted )
      updatedFiles.getGroupById( FileGroup.REMOVED_FROM_REPOSITORY_ID ).add(rootPath + path, vcsKey, null);
  }

  @Nullable
  public Configurable createConfigurable( Collection<FilePath> files )
  {
    return null;
  }

  public boolean validateOptions(final Collection<FilePath> roots) {
    return true;
  }
}
