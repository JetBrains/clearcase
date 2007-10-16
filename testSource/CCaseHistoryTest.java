import junit.framework.TestCase;
import net.sourceforge.transparent.History.CCaseHistoryParser;

import java.util.ArrayList;

public class CCaseHistoryTest extends TestCase
{
  public void testCase0()
  {
    String testString =
    "16-Oct.10:20   jeffreyt   checkout version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java\" from \\main\\selfservice3_integration\\jeffreyt_sss3_20070802\\51 (reserved)\n" +
    "16-Oct.10:08   ivanovs    checkout version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java\" from \\main\\selfservice3_integration\\ivanovs_sss3_20070905\\13 (unreserved)\n" +
    "16-Oct.09:26   byrnesg    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\byrnesg_rfq_phase3_self_service00\\27\"\n" +
    "15-Oct.19:27   pardesis   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\pardesis_ss3_20070509\\34\"\n" +
    "15-Oct.17:40   jeffreyt   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\52\" (SELFSERVICE_RC37)\n" +
    "15-Oct.15:48   collinj    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\full_edms_fx_integration\\2\"\n" +
    "  \"it's possible for clearscreen to be called before the window holder is set so now handles that case\"\n" +
    "15-Oct.15:45   collinj    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\collinj_fulledmsfx_barx_20071001\\2\"\n" +
    "  \"it's possible for clearscreen to be called before the window holder has been set therefore added an extra check for glass being null\"\n" +
    "15-Oct.15:38   ivanovs    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\ivanovs_sss3_20070905\\13\"\n" +
    "15-Oct.09:21   jeffreyt   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\jeffreyt_sss3_20070802\\51\"\n" +
    "12-Oct.17:42   collinj    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\full_edms_fx_integration\\1\"\n" +
    "  \"gui refresh\"\n" +
    "12-Oct.17:41   collinj    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\full_edms_fx_integration\\0\"\n" +
    "12-Oct.17:41   collinj    create branch \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\full_edms_fx_integration\"\n" +
    "  \"gui refresh\"\n" +
    "12-Oct.17:40   collinj    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\collinj_fulledmsfx_barx_20071001\\1\"\n" +
    "  \"gui refresh\"\n" +
    "12-Oct.17:23   byrnesg    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\byrnesg_rfq_phase3_self_service00\\26\"\n" +
    "12-Oct.16:41   jeffreyt   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\51\"\n" +
    "12-Oct.16:39   jeffreyt   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\jeffreyt_sss3_20070802\\50\"\n" +
    "12-Oct.16:27   jeffreyt   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\50\"\n" +
    "12-Oct.16:25   jeffreyt   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\jeffreyt_sss3_20070802\\49\"\n" +
    "12-Oct.16:09   jeffreyt   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\49\"\n" +
    "12-Oct.15:32   jeffreyt   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\jeffreyt_sss3_20070802\\48\"\n" +
    "12-Oct.14:08   collinj    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\collinj_fulledmsfx_barx_20071001\\0\"\n" +
    "12-Oct.14:08   collinj    create branch \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\collinj_fulledmsfx_barx_20071001\"\n" +
    "12-Oct.13:53   tanj       create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\jt_sss\\15\"\n" +
    "12-Oct.12:06   ivanovs    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\ivanovs_sss3_20070905\\12\"\n" +
    "26-Sep.13:43   jeffreyt   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\37\" (SELFSERVICE_RC31)\n" +
    "26-Sep.13:39   jeffreyt   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\jeffreyt_sss3_20070802\\32\"\n" +
    "  \"\n" +
    "\n" +
    "   \"\n" +
    "26-Sep.11:37   ivanovs    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\ivanovs_sss3_20070905\\4\"\n" +
    "26-Sep.11:21   byrnesg    create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\byrnesg_rfq_phase3_self_service00\\16\"\n" +
    "26-Sep.10:46   jeffreyt   create version \"Z:/fo_fx_fox/appl/java/src/com/barcap/fox/client/gui/apps/swap/SwapTicket.java@@\\main\\selfservice3_integration\\36\"\n";

    ArrayList<CCaseHistoryParser.SubmissionData> changes = CCaseHistoryParser.parse( testString );
    for( CCaseHistoryParser.SubmissionData change : changes )
    {
      assertTrue( "Action is valid", change.action.equals( "create version" ) ||
                                     change.action.equals( "checkin version" ) ||
                                     change.action.equals( "create branch" ));
    }
  }
}
