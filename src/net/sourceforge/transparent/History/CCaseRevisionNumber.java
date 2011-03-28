package net.sourceforge.transparent.History;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;

/**
* @author irengrig
*         Date: 3/28/11
*         Time: 2:05 PM
*/
public class CCaseRevisionNumber implements VcsRevisionNumber
{
  private final String revision;
  private final int    order;

  public CCaseRevisionNumber(String revision, int order)
  {
    this.revision = revision;
    this.order = order;
  }

  public String asString() { return revision;  }

  public int compareTo( VcsRevisionNumber revisionNumber )
  {
    CCaseRevisionNumber rev = (CCaseRevisionNumber)revisionNumber;
    if( order > rev.order )
      return -1;
    else
    if( order < rev.order )
      return 1;
    else
      return 0;
  }
}
