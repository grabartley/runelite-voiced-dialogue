package com.grahambartley.runelite.voiced.dialogue.panel;

import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceCatalog;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverride;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverrideStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Objects;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.JTextComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

final class NpcDetailView extends JPanel {

  static final int CHATHEAD_SIZE = 48;
  static final int NAME_LIMIT = 40;
  static final int ACCENT_LIMIT = 160;
  static final int STYLE_LIMIT = 400;
  static final int PACE_LIMIT = 120;
  static final String PLACEHOLDER = "Plugin default";
  static final String REVOICE_NOTE =
      "Blank fields keep the plugin's voice. Edited NPCs are re-voiced, and re-billed, the next"
          + " time you hear them.";

  private final NpcVoiceOverrideStore store;
  private final ChatheadImages chatheads;
  private final Runnable onChanged;

  private final JLabel chathead = new JLabel();
  private final JLabel title = new JLabel();
  private final JLabel subtitle = new JLabel();
  private final JComboBox<Integer> npcPicker = new JComboBox<>();
  private final JPanel npcPickerRow = new JPanel(new BorderLayout(0, 2));
  private final JComboBox<VoiceTypeOption> voiceType = new JComboBox<>(VoiceTypeOption.values());
  private final JTextComponent nameField = field(NAME_LIMIT, "e.g. Grizzled Varrock guard");
  private final JTextComponent accentField = field(ACCENT_LIMIT, "e.g. Strong Scottish accent");
  private final PlaceholderTextArea styleField = new PlaceholderTextArea(PLACEHOLDER, 3);
  private final JTextComponent paceField = field(PACE_LIMIT, "e.g. Slow and deliberate");
  private final NpcScopePicker scopePicker;
  private final JButton saveButton = new JButton("Save");
  private final JButton clearButton = new JButton("Clear override");
  private final JLabel status = new JLabel(" ");

  private NpcListEntry entry;
  private boolean loading;

  NpcDetailView(
      NpcVoiceCatalog catalog,
      NpcVoiceOverrideStore store,
      ChatheadImages chatheads,
      Runnable onBack,
      Runnable onChanged) {
    super(new BorderLayout());
    this.store = store;
    this.chatheads = chatheads;
    this.onChanged = onChanged;
    this.scopePicker = new NpcScopePicker(catalog, this::formChanged);
    setBackground(ColorScheme.DARK_GRAY_COLOR);

    JPanel form = new ViewportWidthPanel(new GridBagLayout());
    form.setBackground(ColorScheme.DARK_GRAY_COLOR);
    form.setBorder(new EmptyBorder(0, 0, 8, 0));
    Stack stack = new Stack(form);

    JButton back = new JButton("Back to list");
    back.setFocusable(false);
    back.addActionListener(e -> onBack.run());
    stack.add(back, 10);
    stack.add(header(), 10);

    npcPicker.setRenderer(new NpcIdRenderer());
    npcPicker.addActionListener(e -> pickNpc());
    npcPickerRow.setOpaque(false);
    npcPickerRow.add(caption("Which one"), BorderLayout.NORTH);
    npcPickerRow.add(npcPicker, BorderLayout.CENTER);
    stack.add(npcPickerRow, 10);

    voiceType.setToolTipText("Type A and Type B are the same voices as the Player Voice setting");
    voiceType.addActionListener(e -> formChanged());
    stack.add(labelled("Voice type", voiceType), 8);
    stack.add(labelled("Character name", nameField), 8);
    stack.add(labelled("Accent", accentField), 8);
    styleField.setToolTipText("e.g. A gruff veteran who has seen too many goblin raids");
    limit(styleField, STYLE_LIMIT);
    styleField.setBorder(new EmptyBorder(4, 6, 4, 6));
    stack.add(labelled("Style", styleField), 8);
    stack.add(labelled("Pace", paceField), 12);
    stack.add(caption("Apply to"), 2);
    stack.add(scopePicker, 10);
    stack.add(new WrappingLabel(REVOICE_NOTE, ColorScheme.LIGHT_GRAY_COLOR), 8);

    JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 6));
    buttons.setOpaque(false);
    saveButton.addActionListener(e -> save());
    clearButton.addActionListener(e -> clear());
    buttons.add(saveButton);
    buttons.add(clearButton);
    stack.add(buttons, 6);
    status.setFont(FontManager.getRunescapeSmallFont());
    stack.add(status, 0);
    stack.fill();

    JScrollPane scroll = new JScrollPane(form);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);
  }

  void open(NpcListEntry opened) {
    entry = opened;
    title.setText(opened.name());
    subtitle.setText(opened.heard() ? "Heard this session" : NpcListRow.detailText(opened));
    chatheads.load(opened.name(), CHATHEAD_SIZE, chathead::setIcon);
    loading = true;
    npcPicker.removeAllItems();
    opened.ids().forEach(npcPicker::addItem);
    npcPicker.setSelectedItem(opened.preferredId());
    npcPickerRow.setVisible(opened.ids().size() > 1);
    loading = false;
    status.setText(" ");
    loadNpc(opened.preferredId());
  }

  private void pickNpc() {
    Integer picked = (Integer) npcPicker.getSelectedItem();
    if (!loading && picked != null) {
      status.setText(" ");
      loadNpc(picked);
    }
  }

  private void loadNpc(int npcId) {
    loading = true;
    NpcVoiceOverride saved = store.get(npcId);
    voiceType.setSelectedItem(VoiceTypeOption.of(saved == null ? null : saved.voiceType()));
    nameField.setText(saved == null ? "" : orEmpty(saved.name()));
    accentField.setText(saved == null ? "" : orEmpty(saved.accent()));
    styleField.setText(saved == null ? "" : orEmpty(saved.style()));
    paceField.setText(saved == null ? "" : orEmpty(saved.pace()));
    scopePicker.show(npcId, entry.name(), entry.ids());
    loading = false;
    formChanged();
  }

  NpcVoiceOverride formOverride() {
    return new NpcVoiceOverride(
        blankToNull(nameField.getText()),
        blankToNull(accentField.getText()),
        blankToNull(styleField.getText()),
        blankToNull(paceField.getText()),
        ((VoiceTypeOption) voiceType.getSelectedItem()).voiceType());
  }

  private void formChanged() {
    if (loading) {
      return;
    }
    NpcVoiceOverride form = emptyToNull(formOverride());
    boolean wouldChange = false;
    boolean anySaved = false;
    for (int id : scopePicker.selectedIds()) {
      NpcVoiceOverride saved = store.get(id);
      anySaved |= saved != null;
      wouldChange |= !Objects.equals(saved, form);
    }
    saveButton.setEnabled(wouldChange);
    clearButton.setEnabled(anySaved);
  }

  private void save() {
    NpcVoiceOverride form = emptyToNull(formOverride());
    int saved = 0;
    int cleared = 0;
    for (int id : scopePicker.selectedIds()) {
      boolean hadOverride = store.get(id) != null;
      store.set(id, form);
      if (store.get(id) != null) {
        saved++;
      } else if (hadOverride) {
        cleared++;
      }
    }
    finish(saveMessage(saved, cleared), ColorScheme.PROGRESS_COMPLETE_COLOR);
  }

  static String saveMessage(int saved, int cleared) {
    if (saved > 0) {
      return "Saved for " + npcCount(saved) + ".";
    }
    return cleared > 0 ? "Cleared " + npcCount(cleared) + "." : "Nothing to save.";
  }

  void reload() {
    if (entry != null) {
      Integer selected = (Integer) npcPicker.getSelectedItem();
      loadNpc(selected == null ? entry.preferredId() : selected);
    }
  }

  private void clear() {
    int cleared = 0;
    for (int id : scopePicker.selectedIds()) {
      if (store.get(id) != null) {
        store.clear(id);
        cleared++;
      }
    }
    finish(
        "Cleared " + npcCount(cleared) + ". The plugin's voice is back.",
        ColorScheme.LIGHT_GRAY_COLOR);
  }

  private void finish(String message, Color color) {
    reload();
    status.setForeground(color);
    status.setText(message);
    onChanged.run();
  }

  private JPanel header() {
    JPanel header = new JPanel(new BorderLayout(10, 0));
    header.setOpaque(false);
    header.add(chathead, BorderLayout.WEST);
    JPanel text = new JPanel(new GridLayout(2, 1));
    text.setOpaque(false);
    title.setFont(FontManager.getRunescapeBoldFont());
    title.setForeground(Color.WHITE);
    subtitle.setFont(FontManager.getRunescapeSmallFont());
    subtitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    text.add(title);
    text.add(subtitle);
    header.add(text, BorderLayout.CENTER);
    return header;
  }

  private JTextComponent field(int maxLength, String tooltip) {
    JTextField field = new JTextField();
    field.putClientProperty("JTextField.placeholderText", PLACEHOLDER);
    field.setToolTipText(tooltip);
    limit(field, maxLength);
    return field;
  }

  private void limit(JTextComponent component, int maxLength) {
    ((AbstractDocument) component.getDocument()).setDocumentFilter(new MaxLengthFilter(maxLength));
    component
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                formChanged();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                formChanged();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                formChanged();
              }
            });
  }

  private static JPanel labelled(String label, JComponent input) {
    JPanel panel = new JPanel(new BorderLayout(0, 2));
    panel.setOpaque(false);
    panel.add(caption(label), BorderLayout.NORTH);
    panel.add(input, BorderLayout.CENTER);
    return panel;
  }

  private static JLabel caption(String text) {
    JLabel label = new JLabel(text);
    label.setFont(FontManager.getRunescapeSmallFont());
    label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    return label;
  }

  static String npcCount(int count) {
    return count == 1 ? "1 NPC" : count + " NPCs";
  }

  private static NpcVoiceOverride emptyToNull(NpcVoiceOverride override) {
    return override.isEmpty() ? null : override;
  }

  private static String blankToNull(String text) {
    String trimmed = text == null ? "" : text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String orEmpty(String text) {
    return text == null ? "" : text;
  }

  JComboBox<Integer> npcPicker() {
    return npcPicker;
  }

  boolean npcPickerShown() {
    return npcPickerRow.isVisible();
  }

  JComboBox<VoiceTypeOption> voiceType() {
    return voiceType;
  }

  JTextComponent nameField() {
    return nameField;
  }

  JTextComponent accentField() {
    return accentField;
  }

  PlaceholderTextArea styleField() {
    return styleField;
  }

  JTextComponent paceField() {
    return paceField;
  }

  NpcScopePicker scopePicker() {
    return scopePicker;
  }

  JButton saveButton() {
    return saveButton;
  }

  JButton clearButton() {
    return clearButton;
  }

  String statusText() {
    return status.getText();
  }

  String titleText() {
    return title.getText();
  }

  private final class NpcIdRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof Integer) {
        int id = (Integer) value;
        String text = "NPC #" + id;
        if (entry != null && entry.heard() && id == entry.preferredId()) {
          text += " (heard)";
        }
        if (store.get(id) != null) {
          text += " (edited)";
        }
        setText(text);
      }
      return this;
    }
  }

  private static final class Stack {
    private final JPanel panel;
    private int row;

    private Stack(JPanel panel) {
      this.panel = panel;
    }

    private void add(JComponent component, int gapBelow) {
      GridBagConstraints c = new GridBagConstraints();
      c.gridx = 0;
      c.gridy = row++;
      c.weightx = 1;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.insets = new Insets(0, 0, gapBelow, 0);
      panel.add(component, c);
    }

    private void fill() {
      GridBagConstraints c = new GridBagConstraints();
      c.gridx = 0;
      c.gridy = row++;
      c.weighty = 1;
      c.fill = GridBagConstraints.VERTICAL;
      JPanel filler = new JPanel();
      filler.setOpaque(false);
      panel.add(filler, c);
    }
  }
}
