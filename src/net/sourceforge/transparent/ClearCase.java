// Decompiled by Jad v1.5.8e. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/kpdus/jad.html
// Decompiler options: packimports(3) braces fieldsfirst splitstr(nl) nonlb space 
// Source File Name:   ClearCase.java

package net.sourceforge.transparent;

import java.io.File;

// Referenced classes of package net.sourceforge.transparent:
//            Status, CheckedOutStatus

public interface ClearCase
{
    String getName();

    void checkIn(File file, String s);
    void checkOut(File file, boolean flag, String comment);
    void undoCheckOut(File file);
    void add(File file, String s);
    void delete(File file, String s);
    void move(File file, File file1, String s);

    Status getStatus(File file);
    boolean isElement(File file);
    boolean isCheckedOut(File file);

    void cleartool(String s);
    CheckedOutStatus getCheckedOutStatus(File file);
    String getCheckoutComment(File file);
}
