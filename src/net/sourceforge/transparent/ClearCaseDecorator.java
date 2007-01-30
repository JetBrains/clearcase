package net.sourceforge.transparent;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;

public class ClearCaseDecorator
        implements ClearCase {

    public static final Logger LOG = Logger.getInstance("net.sourceforge.transparent.ClearCase");

    private ClearCase clearCase;


    public ClearCaseDecorator(ClearCase clearcase) {
        clearCase = clearcase;
    }

    public String getName() {
        return clearCase.getName();
    }

    public ClearCase getClearCase() {
        return clearCase;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClearCaseDecorator)) {
            return false;
        }
        ClearCaseDecorator clearCaseDecorator = (ClearCaseDecorator) o;
        return clearCase.equals(clearCaseDecorator.clearCase);
    }

    public int hashCode() {
        return clearCase.hashCode();
    }

    public void move(File file, File target, String comment) {
        debug("move of " + file.getPath() + " to " + target.getPath());
        clearCase.move(file, target, comment);
    }

    public void undoCheckOut(File file) {
        debug("uncheckout of " + file.getPath());
        clearCase.undoCheckOut(file);
    }

    public void checkIn(File file, String comment) {
        debug("checkin of " + file.getPath());
        clearCase.checkIn(file, comment);
    }

    public void checkOut(File file, boolean isReserved, String comment) {
        debug("checkout of " + file.getPath());
        clearCase.checkOut(file, isReserved, comment);
    }

    public void delete(File file, String comment) {
        debug("delete of " + file.getPath());
        clearCase.delete(file, comment);
    }

    public void add(File file, String comment) {
        debug("add of " + file);
        clearCase.add(file, comment);
    }

    public Status getStatus(File file) {
        Status status = clearCase.getStatus(file);
        debug("status of " + file + "=" + status);
        return status;
    }

    public boolean isElement(File file) {
        return clearCase.isElement(file);
    }

    public boolean isCheckedOut(File file) {
        return clearCase.isCheckedOut(file);
    }

    public void cleartool(String cmd) {
        debug("executing cleartool " + cmd);
        clearCase.cleartool(cmd);
    }

    public CheckedOutStatus getCheckedOutStatus(File file) {
        CheckedOutStatus status = clearCase.getCheckedOutStatus(file);
        debug("getCheckedOutStatus of " + file + "=" + status);
        return status;
    }

    public String getCheckoutComment(File file) {
        String comment = clearCase.getCheckoutComment(file);
        debug("getCheckoutComment of " + file + "=" + comment);
        return comment;
    }

    public static void debug(String message) {
        if (LOG.isDebugEnabled()) {
            LOG.debug(message);
        }
    }
}
