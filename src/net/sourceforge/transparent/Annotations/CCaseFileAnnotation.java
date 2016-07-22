package net.sourceforge.transparent.Annotations;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Sep 5, 2007
 */
public class CCaseFileAnnotation extends FileAnnotation
{
  private final StringBuffer myContentBuffer = new StringBuffer();
  private final List<LineInfo> myLineInfos = new ArrayList<>();
  private final VirtualFile myFile;
  private VFSForAnnotationListener myListener;

  public CCaseFileAnnotation(Project project, final VirtualFile file) {
    super(project);
    myFile = file;
    myListener = new VFSForAnnotationListener(file, this);
    VirtualFileManager.getInstance().addVirtualFileListener(myListener);
  }

  static class LineInfo
  {
    private final String date;
    private final String revisionSig;
    private final String author;

    public LineInfo( final String date, final String rev, final String author )
    {
      this.date = date;
      revisionSig = rev;
      this.author = author;
    }

    public String getDate()     {  return date;  }
    public String getRevision() {  return revisionSig;  }
    public String getAuthor()   {  return author;  }
  }

  private final LineAnnotationAspect DATE_ASPECT = new CCAnnotationAspect(CCAnnotationAspect.DATE, true)
  {
    public String getValue( int lineNumber )
    {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0)
        return "";
      else
        return myLineInfos.get( lineNumber ).getDate();
    }
  };

  private final LineAnnotationAspect REVISION_ASPECT = new CCAnnotationAspect(CCAnnotationAspect.REVISION, false)
  {
    public String getValue(int lineNumber)
    {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0)
        return "";
      else
        return myLineInfos.get(lineNumber).getRevision();
    }
  };

  private final LineAnnotationAspect AUTHOR_ASPECT = new CCAnnotationAspect(CCAnnotationAspect.AUTHOR, true)
  {
    public String getValue(int lineNumber)
    {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0)
        return "";
      else
        return myLineInfos.get(lineNumber).getAuthor();
    }
  };

  public void dispose() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myListener);
  }

  public String getToolTip(int lineNumber)
  {
    if (myLineInfos.size() <= lineNumber || lineNumber < 0) {
      return "";
    }
    final LineInfo info = myLineInfos.get(lineNumber);
    return info.getRevision();
  }

  public LineAnnotationAspect[] getAspects()
  {
    return new LineAnnotationAspect[]{ REVISION_ASPECT, DATE_ASPECT, AUTHOR_ASPECT };
  }

  public String getAnnotatedContent()
  {
    return myContentBuffer.toString();
  }

  @Nullable
  public VcsRevisionNumber getLineRevisionNumber(final int lineNumber) {
    return null;
  }

  @Override
  public Date getLineDate(int lineNumber) {
    if (myLineInfos.size() <= lineNumber || lineNumber < 0) {
      return null;
    }
    final LineInfo info = myLineInfos.get(lineNumber);
    // todo
    return null;
  }

  public VcsRevisionNumber originalRevision(int lineNumber) {
    return null;
  }

  @Nullable
  public List<VcsFileRevision> getRevisions() {
    return null;
  }

  public boolean revisionsNotEmpty() {
    return false;
  }

  public AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    return null;
  }

  @Override
  public int getLineCount() {
    return myLineInfos.size();
  }

  public void addLineInfo( final String date, final String revision, final String author, final String line)
  {
    myLineInfos.add( new LineInfo( date, revision, author ) );
    myContentBuffer.append( line );
    myContentBuffer.append( "\n" );
  }

  private abstract class CCAnnotationAspect extends LineAnnotationAspectAdapter {
    protected CCAnnotationAspect() {
      super();
    }

    protected CCAnnotationAspect(String id, boolean showByDefault) {
      super(id, showByDefault);
    }

    @Override
    protected void showAffectedPaths(int lineNum) {
      //todo
    }
  }

  @Nullable
  @Override
  public VcsRevisionNumber getCurrentRevision() {
    return null;
  }

  @Override
  public VcsKey getVcsKey() {
    return TransparentVcs.getKey();
  }

  @Override
  public VirtualFile getFile() {
    return myFile;
  }
}
