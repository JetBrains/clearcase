// Decompiled by Jad v1.5.8e. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/kpdus/jad.html
// Decompiler options: packimports(3) braces fieldsfirst splitstr(nl) nonlb space 
// Source File Name:   ClearCase.java

package net.sourceforge.transparent;

import java.io.File;

// Referenced classes of package net.sourceforge.transparent:
//            Status, CheckedOutStatus

public interface ClearCase {

    public abstract String getName();

    public abstract void checkIn(File file, String s);

    public abstract void checkOut(File file, boolean flag, String comment);

    public abstract void undoCheckOut(File file);

    public abstract void add(File file, String s);

    public abstract void delete(File file, String s);

    public abstract void move(File file, File file1, String s);

    public abstract Status getStatus(File file);

    public abstract boolean isElement(File file);

    public abstract boolean isCheckedOut(File file);

    public abstract void cleartool(String s);

    public abstract CheckedOutStatus getCheckedOutStatus(File file);

    public abstract String getCheckoutComment(File file);
}
