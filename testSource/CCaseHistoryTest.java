import junit.framework.Assert;
import junit.framework.TestCase;
import net.sourceforge.transparent.History.CCaseHistoryParser;

import java.util.ArrayList;

public class CCaseHistoryTest extends TestCase
{
  public void testBeginningWithDigitAndQuotes() throws Throwable {
    final String contents = "0\u000104-фев-09.19:46:00\n" +
                            "1\u0001Irina Chernushina\n" +
                            "2\u0001checkout version\n" +
                            "3\u0001\n" +
                            "4\u0001C:/TestProjects/ccase/test/it_test/ITVob/src/com/refactoring/users/User1.java\n" +
                            "5\u0001related issues: \"some quoted content\"\n" +
                            "0\u000104-фев-09.17:51:32\n" +
                            "1\u0001Irina Chernushina\n" +
                            "2\u0001create version\n" +
                            "3\u0001\n" +
                            "4\u0001C:/TestProjects/ccase/test/it_test/ITVob/src/com/refactoring/users/User1.java@@\\main\\it_test\\3\n" +
                            "5\u0001related issues:\n" +
                            "0 - scr1\n" +
                            "1 - scr2\n" +
                            "2 - scr3";
    final ArrayList<CCaseHistoryParser.SubmissionData> changes = CCaseHistoryParser.parse( contents );
    Assert.assertEquals(changes.size(), 2);
    Assert.assertEquals("related issues: \"some quoted content\"", changes.get(0).comment);
    Assert.assertEquals("checkout version", changes.get(0).action);
    Assert.assertEquals("04-фев-09.19:46:00", changes.get(0).changeDate);

    Assert.assertEquals("related issues:\n0 - scr1\n1 - scr2\n2 - scr3", changes.get(1).comment);
    Assert.assertEquals("create version", changes.get(1).action);
    Assert.assertEquals("04-фев-09.17:51:32", changes.get(1).changeDate);
  }
}
