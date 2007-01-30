// Decompiled by Jad v1.5.8e. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/kpdus/jad.html
// Decompiler options: packimports(3) braces fieldsfirst splitstr(nl) nonlb space 
// Source File Name:   Clearcase.java

package net.sourceforge.eclipseccase.jni;

public class Clearcase {
    public static class Status {

        public boolean status;
        public String message;

        public Status(boolean status, String message) {
            this.status = status;
            this.message = message;
        }
    }


    public static native Status checkout(String s, String s1, boolean flag, boolean flag1);

    public static native Status checkin(String s, String s1, boolean flag);

    public static native Status uncheckout(String s, boolean flag);

    public static native Status add(String s, String s1, boolean flag);

    public static native Status delete(String s, String s1);

    public static native Status move(String s, String s1, String s2);

    public static native Status cleartool(String s);

    public static native boolean isCheckedOut(String s);

    public static native boolean isElement(String s);

    public static native boolean isDifferent(String s);

    public static native boolean isSnapShot(String s);

    public static native boolean isHijacked(String s);

    public static void main(String args[]) {
        if (args.length == 0) {
            System.out.println("Usage: Clearcase existing_ccase_elt nonexisting_ccase_elt");
            System.exit(1);
        }
        String file = args[0];
        System.out.println("isElement: " + isElement(file));
        System.out.println("isCheckedOut: " + isCheckedOut(file));
        System.out.println("checkout: " + checkout(file, "", false, true).message);
        System.out.println("isCheckedOut: " + isCheckedOut(file));
        System.out.println("uncheckout: " + uncheckout(file, false).message);
        System.out.println("isCheckedOut: " + isCheckedOut(file));
        if (args.length > 1) {
            String newfile = args[1];
            System.out.println("isElement: " + isElement(newfile));
            System.out.println("add: " + add(newfile, "", false).message);
            System.out.println("isElement: " + isElement(newfile));
            System.out.println("checkin: " + checkin(newfile, "", true).message);
            System.out.println("delete: " + delete(newfile, "").message);
            System.out.println("isElement: " + isElement(newfile));
        }
    }

    private Clearcase() {
    }

    private static native void initialize();

    static  {
        System.loadLibrary("ccjni");
        initialize();
    }
}
