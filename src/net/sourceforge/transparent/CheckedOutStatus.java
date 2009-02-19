// Decompiled by Jad v1.5.8e. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/kpdus/jad.html
// Decompiler options: packimports(3) braces fieldsfirst splitstr(nl) nonlb space 
// Source File Name:   CheckedOutStatus.java

package net.sourceforge.transparent;


public class CheckedOutStatus {

    public static final CheckedOutStatus RESERVED = new CheckedOutStatus("reserved");
    public static final CheckedOutStatus UNRESERVED = new CheckedOutStatus("unreserved");
    public static final CheckedOutStatus NOT_CHECKED_OUT = new CheckedOutStatus("not checked out");
    private final String _checkedOutState;

    private CheckedOutStatus(String state) {
        _checkedOutState = state;
    }

    public String toString() {
        return _checkedOutState;
    }

}
