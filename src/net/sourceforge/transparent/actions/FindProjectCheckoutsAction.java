package net.sourceforge.transparent.actions;

import net.sourceforge.transparent.TransparentVcs;

public class FindProjectCheckoutsAction extends FindCheckoutsAction
{
  protected String getTargetPath( TransparentVcs vcs, String filePath )
  {
    return vcs.getConfig().clearcaseRoot;
  }
}
