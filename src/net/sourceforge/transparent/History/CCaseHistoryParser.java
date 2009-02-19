package net.sourceforge.transparent.History;

import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Jan 26, 2007
 */
public class CCaseHistoryParser
{
  @NonNls public static final String BRANCH_COMMAND_SIG = "create branch";
  @NonNls public static final String CREATE_ELEM_COMMAND_SIG = "create file element";

  @NonNls private static final String VERSION_DELIM = "@@";
  @NonNls private static final String[] actions = { CREATE_ELEM_COMMAND_SIG, BRANCH_COMMAND_SIG, "create version", "checkin version" };
  private static final String FMT = "-fmt";

  private CCaseHistoryParser() {}

  public static class SubmissionData
  {
    public SubmissionData(final int order) {
      this.order = order;
    }

    public String action;
    public String version;
    public String submitter;
    public String changeDate;
    public String comment;
    public String labels;
    public int    order;
  }

  public static ArrayList<SubmissionData> parse( final String content )
  {
    String[] lines = LineTokenizer.tokenize( content, false );

    final LogParseResult resultHolder = new LogParseResult();
    final FieldsDetector detector = new FieldsDetector();

    for( String line : lines )
    {
      final Field field = detector.guess(line);
      if (field != null) {
        field.parse(line, resultHolder);
      } else {
        final Field defaultField = detector.defaultField();
        if (defaultField != null) {
          defaultField.fill(line, resultHolder);
        }
      }
    }
    return resultHolder.getResult();
  }

  private static class FieldsDetector {
    private int myRecentIdx;

    private FieldsDetector() {
      myRecentIdx = 0;
    }

    @Nullable
    public Field guess(final String line) {
      int guessIdx = (myRecentIdx + 1 == ourFields.length) ? 0 : (myRecentIdx + 1);
      if (ourFields[guessIdx].acceptString(line)) {
        myRecentIdx = guessIdx;
        return ourFields[guessIdx];
      }
      for (int i = 0; i < ourFields.length; i++) {
        final Field field = ourFields[i];
        if (field.acceptString(line)) {
          myRecentIdx = i;
          return field;
        }
      }
      return null;
    }

    @Nullable
    public Field defaultField() {
      int oldRecentIdx = myRecentIdx;
      // if there was a comment field before
      if (ourFields[oldRecentIdx].myNum == 5) {
        myRecentIdx = oldRecentIdx;
        return ourFields[oldRecentIdx];
      }
      return null;
    }
  }

  public static void fillParametersVersionOnly(final List<String> list) {
    list.add(FMT);
    final StringBuilder sb = new StringBuilder();
    ourFields[4].append(sb);
    list.add(sb.toString());

  }

  public static void fillParametersTail(final List<String> list) {
    list.add(FMT);
    final StringBuilder sb = new StringBuilder();
    for (Field field : ourFields) {
      field.append(sb);
    }
    list.add(sb.toString());
  }

  private static final Field[] ourFields = new Field[] {
    new Field(0,"\\\"", "\"", "%d") {
      protected void fill(@Nullable String value, CreatingIterator iterator) {
        final SubmissionData data = iterator.createNext();
        data.changeDate = value == null ? "" : value;
      }
    },
    new Field(1,"\\\"", "\"", "%Fu") {
      protected void fill(@Nullable String value, CreatingIterator iterator) {
        final SubmissionData data = iterator.getCurrent();
        data.submitter = value == null ? "" : value;
      }
    },
    new Field(2,"\\\"", "\"", "%e") {
      protected void fill(@Nullable String value, CreatingIterator iterator) {
        final SubmissionData data = iterator.getCurrent();
        data.action = value == null ? "" : value;
      }
    },
    new Field(3,"\\\"", "\"", "%l") {
      protected void fill(@Nullable String value, CreatingIterator iterator) {
        final SubmissionData data = iterator.getCurrent();
        data.labels = value == null ? "" : value;
      }
    },
    new Field(4,"\\\"", "\"", "%n") {
      @Override
      protected String parseImpl(String s) {
        final String parsed = super.parseImpl(s);
        final int idx = parsed.indexOf(VERSION_DELIM);
        if (idx != -1) {
          return parsed.substring(idx);
        }
        return "";
      }

      protected void fill(@Nullable String value, CreatingIterator iterator) {
        SubmissionData data = iterator.getCurrent();
        if (data.version != null) {
          data = iterator.createNext();
        }
        data.version = value == null ? "" : value;
      }
    },
    new Field(5,"\\\"", "\"", "%Nc") {
      @Override
      public void parse(String s, CreatingIterator iterator) {
        super.parse(s, iterator);
      }

      protected void fill(@Nullable String value, CreatingIterator iterator) {
        final SubmissionData data = iterator.getCurrent();
        if (value != null) {
          data.comment = (data.comment == null) ? value : data.comment + '\n' + value;
        }
      }
    }
  };

  private abstract static class Field {
    private final int myNum;
    private final String myText;
    private final static String myMagic = "\1";

    public Field(final int num, final String writeWrapper, String readWrapper, final String text) {
      myNum = num;
      myText = text;
    }

    public void append(final StringBuilder sb) {
      sb.append(myNum);
      sb.append(myMagic);
      sb.append(myText);
      sb.append('\n');
    }

    public boolean acceptString(final String s) {
      return StringUtil.startsWithConcatenationOf(s, String.valueOf(myNum), myMagic);
    }

    protected abstract void fill(@Nullable final String value, final CreatingIterator iterator);

    @Nullable
    protected String parseImpl(final String s) {
      if (acceptString(s)) {
        final String result = s.substring(2);
        return result;
      }
      return null;
    }

    public void parse(final String s, final CreatingIterator iterator) {
      fill(parseImpl(s), iterator);
    }
  }

  private interface CreatingIterator {
    @NotNull SubmissionData getCurrent();
    @NotNull SubmissionData createNext();
  }

  private static class LogParseResult implements CreatingIterator {
    private final ArrayList<SubmissionData> myResult;

    private LogParseResult() {
      myResult = new ArrayList<SubmissionData>();
    }

    @NotNull
    public SubmissionData getCurrent() {
      if (myResult.isEmpty()) {
        myResult.add(new SubmissionData(myResult.size()));
      }
      return myResult.get(myResult.size() - 1);
    }

    @NotNull
    public SubmissionData createNext() {
      myResult.add(new SubmissionData(myResult.size()));
      return myResult.get(myResult.size() - 1);
    }

    public ArrayList<SubmissionData> getResult() {
      return myResult;
    }
  }
}
