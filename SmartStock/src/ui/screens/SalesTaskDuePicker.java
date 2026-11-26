package ui.screens;

import ui.screens.customorders.DatePickerField;
import javax.swing.*;
import java.awt.*;
import java.time.*;

/** Store-local calendar and time choices; an unchecked deadline stays optional. */
final class SalesTaskDuePicker extends JPanel {
    final JCheckBox asap=new JCheckBox("ASAP");
    final JCheckBox enabled=new JCheckBox("Set deadline");
    final DatePickerField date=new DatePickerField();
    final JComboBox<Integer> hour=new JComboBox<>();
    final JComboBox<String> minute=new JComboBox<>();
    final JComboBox<String> period=new JComboBox<>(new String[]{"AM","PM"});
    private final ZoneId zone;
    private final Clock clock;
    private final JButton calendar=new JButton("Calendar…"),today=new JButton("Today"),tomorrow=new JButton("Tomorrow");

    SalesTaskDuePicker(ZoneId zone,OffsetDateTime value){this(zone,value,Clock.system(zone));}
    SalesTaskDuePicker(ZoneId zone,OffsetDateTime value,Clock clock) {
        super(new BorderLayout(0,4));this.zone=zone;this.clock=clock;
        for(int i=1;i<=12;i++)hour.addItem(i);
        for(int i=0;i<60;i++)minute.addItem(String.format(java.util.Locale.ROOT,"%02d",i));
        date.setEditable(false);date.setToolTipText("Select a date from the calendar");
        JPanel choices=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));
        choices.add(date);choices.add(calendar);choices.add(hour);choices.add(new JLabel(":"));choices.add(minute);choices.add(period);
        JPanel shortcuts=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));shortcuts.add(today);shortcuts.add(tomorrow);
        JLabel timezone=new JLabel(zone.getId());timezone.setToolTipText("Deadline uses the store's local time zone");shortcuts.add(timezone);
        JPanel modes=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));modes.add(enabled);modes.add(asap);add(modes,BorderLayout.NORTH);add(choices,BorderLayout.CENTER);add(shortcuts,BorderLayout.SOUTH);
        var local=value==null?ZonedDateTime.now(clock.withZone(zone)):value.atZoneSameInstant(zone);
        date.setSelectedDate(local.toLocalDate());hour.setSelectedItem(local.getHour()%12==0?12:local.getHour()%12);
        minute.setSelectedItem(String.format(java.util.Locale.ROOT,"%02d",local.getMinute()));period.setSelectedItem(local.getHour()<12?"AM":"PM");
        enabled.setSelected(value!=null);enabled.addActionListener(e->{if(enabled.isSelected())asap.setSelected(false);updateEnabled();});
        asap.addActionListener(e->{if(asap.isSelected())enabled.setSelected(false);updateEnabled();});
        calendar.addActionListener(e->date.showDatePicker());
        today.addActionListener(e->date.setSelectedDate(LocalDate.now(clock.withZone(zone))));
        tomorrow.addActionListener(e->date.setSelectedDate(LocalDate.now(clock.withZone(zone)).plusDays(1)));
        date.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            private void changed(){SwingUtilities.invokeLater(()->{if(date.getSelectedDate()==null){enabled.setSelected(false);updateEnabled();}});}
            public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}
        });
        updateEnabled();
    }
    private void updateEnabled(){for(JComponent c:new JComponent[]{date,hour,minute,period,calendar,today,tomorrow})c.setEnabled(enabled.isSelected());}
    void setAsap(boolean value){asap.setSelected(value);if(value)enabled.setSelected(false);updateEnabled();}
    boolean dueAsap(){return asap.isSelected();}
    String dueAt() {
        if(!enabled.isSelected())return "";
        LocalDate chosen=date.getSelectedDate();
        if(chosen==null)throw new IllegalArgumentException("Choose a due date.");
        int h=((Integer)hour.getSelectedItem())%12+("PM".equals(period.getSelectedItem())?12:0);
        return chosen.atTime(h,Integer.parseInt((String)minute.getSelectedItem())).atZone(zone).toOffsetDateTime().toString();
    }
}
