package net.sourceforge.transparent.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.OptionsDialog;
import net.sourceforge.transparent.TransparentVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

public class CheckoutDialog extends OptionsDialog implements Refreshable
{
  @NonNls private final static String TITLE = "Checkout Comment";
  @NonNls private final static String FILES_SUFFIX = " files";

  private final String              myLabel;
  private final JTextArea           myCommentArea = new JTextArea();
  private final VcsConfiguration    myConfiguration;
  protected Collection<Refreshable> myAdditionalComponents = new ArrayList<Refreshable>();

  public CheckoutDialog( Project project, VirtualFile fileToCheckout )
  {
    super( project );
    myConfiguration = VcsConfiguration.getInstance( myProject );
    myLabel = fileToCheckout.getPresentableUrl();
    setTitle( TITLE );
    init();
  }

  public CheckoutDialog( Project project, VirtualFile[] filesToCheckout )
  {
    super( project );
    myConfiguration = VcsConfiguration.getInstance( myProject );
    myLabel = filesToCheckout.length + FILES_SUFFIX;
    setTitle( TITLE );
    init();
  }

  protected boolean isToBeShown()
  {
    return TransparentVcs.getInstance( myProject ).getCheckoutOptions().getValue();
  }

  protected void setToBeShown( boolean value, boolean onOk )
  {
    TransparentVcs.getInstance( myProject ).getCheckoutOptions().setValue( value );
  }

  protected JComponent createCenterPanel()
  {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());

    JPanel commentArea = new JPanel(new BorderLayout());
    commentArea.add(new JLabel(myLabel), BorderLayout.NORTH);
    commentArea.add(new JScrollPane(getCommentArea()), BorderLayout.CENTER);

    panel.add(commentArea, BorderLayout.CENTER);

    return panel;
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {   return myCommentArea;   }
  public String     getComment() {  return myCommentArea.getText().trim();  }

  protected void doOKAction()
  {
    if( myConfiguration.FORCE_NON_EMPTY_COMMENT && getComment().length() == 0 )
    {
      int requestForCheckin = Messages.showYesNoDialog("Check out with empty comment?", "Comment Is Empty",
                                                       Messages.getWarningIcon());
      if( requestForCheckin != OK_EXIT_CODE )
        return;
    }
    myConfiguration.LAST_COMMIT_MESSAGE = getComment();
    try {
        saveState();
        super.doOKAction();
    }
    catch (InputException ex) {  ex.show();  }
  }

  protected JTextArea getCommentArea() {
    initCommentArea();
    return myCommentArea;
  }

  protected boolean shouldSaveOptionsOnCancel()  {   return false;   }

  protected void initCommentArea() {
    myCommentArea.setRows(3);
    myCommentArea.setWrapStyleWord(true);
    myCommentArea.setLineWrap(true);
    myCommentArea.setSelectionStart(0);
    myCommentArea.setSelectionEnd(myCommentArea.getText().length());
  }

  public void refresh() {
    for (Refreshable component : myAdditionalComponents)
        component.refresh();
  }

  public void saveState() {
    for (Refreshable component : myAdditionalComponents)
        component.saveState();
  }

  public void restoreState() {
    for (Refreshable component : myAdditionalComponents)
        component.restoreState();
  }

  public void show()
  {
      refresh();
      super.show();
  }
}