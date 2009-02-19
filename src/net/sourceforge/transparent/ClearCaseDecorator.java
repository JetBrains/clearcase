package net.sourceforge.transparent;

import java.io.File;

public class ClearCaseDecorator implements ClearCase
{
  private final ClearCase clearCase;

  public ClearCaseDecorator(ClearCase clearcase) { clearCase = clearcase;  }

  public String     getName()       {  return clearCase.getName();  }
  public ClearCase  getClearCase()  {  return clearCase;  }
  public int        hashCode()      {  return clearCase.hashCode(); }

  public boolean equals(Object o)
  {
    if (this == o) return true;
    if (!(o instanceof ClearCaseDecorator)) return false;
    ClearCaseDecorator clearCaseDecorator = (ClearCaseDecorator) o;
    return clearCase.equals(clearCaseDecorator.clearCase);
  }

  public void undoCheckOut(File file) {  clearCase.undoCheckOut(file);  }

  public void checkIn(File file, String comment) {  clearCase.checkIn(file, comment);  }

  public void checkOut(File file, boolean isReserved, String comment) {
    clearCase.checkOut(file, isReserved, comment);
  }

  public void add(File file, String comment)    {  clearCase.add(file, comment);     }
  public void delete(File file, String comment) {  clearCase.delete(file, comment);  }
  public void move(File file, File target, String comment) {  clearCase.move(file, target, comment);  }

  public Status   getStatus(File file)    {  return clearCase.getStatus(file);    }
  public boolean  isElement(File file)    {  return clearCase.isElement(file);    }
  public boolean  isCheckedOut(File file) {  return clearCase.isCheckedOut(file); }

  public CheckedOutStatus getCheckedOutStatus(File file) {  return clearCase.getCheckedOutStatus(file);  }
  public String getCheckoutComment(File file) {  return clearCase.getCheckoutComment(file);  }

  public void cleartool(String cmd) {  clearCase.cleartool(cmd);  }
}
