package net.sourceforge.transparent;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.text.InternationalFormatter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: Jun 6, 2007
 */
public class CCaseConfigurable implements Configurable
{
  @NonNls private final static String OPTIONS_SCREEN_NAME = "ClearCase Options";
  
  private JCheckBox myWorkOffline;
  private JCheckBox myReservedCheckoutsCheckBox;
  private JCheckBox myCheckOutForHijacked;
  private JCheckBox myUseUCMModel;
  private JCheckBox myRestrictHistory;
  private JFormattedTextField historyText;
  private JTextField scrText;
  private JPanel myConfigPanel;
  private JCheckBox useIdenticalSwitch;
  private JCheckBox synchActivitiesOnRefresh;

  private final Project project;
  private CCaseConfig vcsConfig;

  public CCaseConfigurable(Project project)
  {
    this.project = project;
//    createUIComponents();
  }

  public void disposeUIResources() {}

  public String getDisplayName()  {  return OPTIONS_SCREEN_NAME;        }
  public Icon   getIcon()         {  return null;  }
  public String getHelpTopic()    {  return "project.propVCSSupport.VCSs.ClearCase";  }

  public JComponent createComponent()
  {
    vcsConfig = CCaseConfig.getInstance(project);
    myWorkOffline.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {  resetCheckInOutCheckboxes();  }
    });
    myRestrictHistory.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {  resetHistoryMargin();  }
    });

    return myConfigPanel;
  }

  private void resetCheckInOutCheckboxes()
  {
    boolean isOffline = myWorkOffline.isSelected();
    myReservedCheckoutsCheckBox.setEnabled( !isOffline );
    myCheckOutForHijacked.setEnabled( !isOffline );
    myUseUCMModel.setEnabled( !isOffline );

    if( isOffline )
      myCheckOutForHijacked.setSelected( true );
  }

  private void resetHistoryMargin()
  {
    historyText.setEnabled( myRestrictHistory.isSelected() );
  }

  public boolean isModified()
  {
    return hasScrTextChanged()
           || vcsConfig.checkoutReserved != myReservedCheckoutsCheckBox.isSelected()
           || vcsConfig.checkInUseHijack != myCheckOutForHijacked.isSelected()
           || vcsConfig.useUcmModel != myUseUCMModel.isSelected()
           || vcsConfig.isOffline != myWorkOffline.isSelected()
           || vcsConfig.isHistoryResticted != myRestrictHistory.isSelected()
           || vcsConfig.getHistoryRevisionsMargin() != getMargin()
           || vcsConfig.useIdenticalSwitch != useIdenticalSwitch.isSelected()
           || vcsConfig.synchActivitiesOnRefresh != synchActivitiesOnRefresh.isSelected();  
  }

  private boolean hasScrTextChanged() {
    return  vcsConfig.scrTextFileName == null ||
           !vcsConfig.scrTextFileName.equals( scrText.getText() );
  }

  public void apply() throws ConfigurationException
  {
    boolean need2ReloadActivities = (vcsConfig.useUcmModel != myUseUCMModel.isSelected()) && myUseUCMModel.isSelected();
    vcsConfig.checkoutReserved = myReservedCheckoutsCheckBox.isSelected();
    vcsConfig.checkInUseHijack = myCheckOutForHijacked.isSelected();
    vcsConfig.useUcmModel = myUseUCMModel.isSelected();
    vcsConfig.setOfflineMode( myWorkOffline.isSelected() );
    vcsConfig.isHistoryResticted = myRestrictHistory.isSelected();
    vcsConfig.setHistoryRevisionsMargin( getMargin() );
    vcsConfig.useIdenticalSwitch = useIdenticalSwitch.isSelected();
    vcsConfig.synchActivitiesOnRefresh = synchActivitiesOnRefresh.isSelected();

    if( need2ReloadActivities )
    {
      CCaseViewsManager.getInstance( project ).extractViewActivities();
    }

    vcsConfig.scrTextFileName = scrText.getText();
  }

  public void reset()
  {
    createComponent();
    scrText.setText( vcsConfig.scrTextFileName );
    myReservedCheckoutsCheckBox.setSelected( vcsConfig.checkoutReserved );
    myCheckOutForHijacked.setSelected( vcsConfig.checkInUseHijack );

    myUseUCMModel.setSelected( vcsConfig.useUcmModel );
    useIdenticalSwitch.setSelected( vcsConfig.useIdenticalSwitch );
    synchActivitiesOnRefresh.setSelected( vcsConfig.synchActivitiesOnRefresh );

    myRestrictHistory.setSelected( vcsConfig.isHistoryResticted );
    historyText.setValue( vcsConfig.getHistoryRevisionsMargin() );
    historyText.setEnabled( vcsConfig.isHistoryResticted );

    CCaseViewsManager mgr = CCaseViewsManager.getInstance( project );
    myWorkOffline.setSelected( mgr.isAnySnapshotView() && vcsConfig.isOffline );
    myWorkOffline.setEnabled( mgr.isAnySnapshotView() );

    resetCheckInOutCheckboxes();
  }

  private int getMargin()
  {
    int margin = vcsConfig.getHistoryRevisionsMargin();
    try
    {
      margin = (int) Long.parseLong( historyText.getText() );
    }
    catch( NumberFormatException e ){
      //  Catastrofic case when formatters suck.
    }
    return margin;
  }

  private void createUIComponents()
  {
    final NumberFormat format = NumberFormat.getIntegerInstance();
    format.setParseIntegerOnly( true );
    format.setMinimumIntegerDigits( 1 );
    format.setMaximumIntegerDigits( 4 );
    format.setGroupingUsed( false );
    
    final InternationalFormatter formatter = new InternationalFormatter(format);
    formatter.setAllowsInvalid(false);
    formatter.setCommitsOnValidEdit(true);
    formatter.setMinimum( 1 );

    historyText = new JFormattedTextField( formatter );
  }
}
