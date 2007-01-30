// Decompiled by Jad v1.5.8e. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/kpdus/jad.html
// Decompiler options: packimports(3) braces fieldsfirst splitstr(nl) nonlb space 
// Source File Name:   FindProjectCheckoutsAction.java

package net.sourceforge.transparent.actions;

import net.sourceforge.transparent.TransparentVcs;

// Referenced classes of package net.sourceforge.transparent.actions:
//            FindCheckoutsAction

public class FindProjectCheckoutsAction extends FindCheckoutsAction
{
  protected String getTargetPath( TransparentVcs vcs, String filePath )
  {
    return vcs.getTransparentConfig().clearcaseRoot;
  }
}
