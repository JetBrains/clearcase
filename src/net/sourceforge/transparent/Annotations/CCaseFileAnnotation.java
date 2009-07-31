package net.sourceforge.transparent.Annotations;

import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Sep 5, 2007
 */
public class CCaseFileAnnotation implements FileAnnotation
{
  private final StringBuffer myContentBuffer = new StringBuffer();
  private final List<LineInfo> myLineInfos = new ArrayList<LineInfo>();
  private final List<AnnotationListener> myListeners = new ArrayList<AnnotationListener>();
  private VFSForAnnotationListener myListener;

  public CCaseFileAnnotation(final VirtualFile file) {
    myListener = new VFSForAnnotationListener(file, myListeners);
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

  private final LineAnnotationAspect DATE_ASPECT = new LineAnnotationAspectAdapter()
  {
    public String getValue( int lineNumber )
    {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0)
        return "";
      else
        return myLineInfos.get( lineNumber ).getDate();
    }
  };

  private final LineAnnotationAspect REVISION_ASPECT = new LineAnnotationAspectAdapter()
  {
    public String getValue(int lineNumber)
    {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0)
        return "";
      else
        return myLineInfos.get(lineNumber).getRevision();
    }
  };

  private final LineAnnotationAspect AUTHOR_ASPECT = new LineAnnotationAspectAdapter()
  {
    public String getValue(int lineNumber)
    {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0)
        return "";
      else
        return myLineInfos.get(lineNumber).getAuthor();
    }
  };


  public void addListener(AnnotationListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(AnnotationListener listener) {
    myListeners.remove(listener);
  }

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

  public VcsRevisionNumber originalRevision(int lineNumber) {
    return null;
  }

  @Nullable
  public List<VcsFileRevision> getRevisions() {
    return null;
  }

  public AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    return null;
  }

  public void addLineInfo( final String date, final String revision, final String author, final String line)
  {
    myLineInfos.add( new LineInfo( date, revision, author ) );
    myContentBuffer.append( line );
    myContentBuffer.append( "\n" );
  }

}
