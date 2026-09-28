package com.grahambartley.runelite.voiced.dialogue.panel;

import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceCatalog;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceScope;
import java.awt.GridLayout;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

final class NpcScopePicker extends JPanel {

  private final NpcVoiceCatalog catalog;
  private final Runnable onChange;
  private final Map<NpcVoiceScope, JRadioButton> buttons = new EnumMap<>(NpcVoiceScope.class);
  private final Map<NpcVoiceScope, List<Integer>> ids = new EnumMap<>(NpcVoiceScope.class);

  NpcScopePicker(NpcVoiceCatalog catalog, Runnable onChange) {
    super(new GridLayout(0, 1, 0, 2));
    this.catalog = catalog;
    this.onChange = onChange;
    setOpaque(false);
    ButtonGroup group = new ButtonGroup();
    for (NpcVoiceScope scope : NpcVoiceScope.values()) {
      JRadioButton button = new JRadioButton();
      button.setOpaque(false);
      button.setFont(FontManager.getRunescapeSmallFont());
      button.setForeground(ColorScheme.TEXT_COLOR);
      button.addActionListener(e -> this.onChange.run());
      group.add(button);
      buttons.put(scope, button);
      add(button);
    }
    buttons.get(NpcVoiceScope.THIS_NPC).setSelected(true);
  }

  void show(int npcId, String npcName, List<Integer> sameNameIds) {
    for (NpcVoiceScope scope : NpcVoiceScope.values()) {
      List<Integer> scoped =
          scope == NpcVoiceScope.SAME_NAME ? sameNameIds : catalog.scopeIds(npcId, scope);
      ids.put(scope, scoped);
      JRadioButton button = buttons.get(scope);
      button.setText(label(scope, npcName, scoped.size()));
      button.setEnabled(scope == NpcVoiceScope.THIS_NPC || scoped.size() > 1);
    }
    NpcVoiceScope selected = selected();
    if (!buttons.get(selected).isEnabled()) {
      buttons.get(NpcVoiceScope.THIS_NPC).setSelected(true);
    }
  }

  static String label(NpcVoiceScope scope, String npcName, int count) {
    switch (scope) {
      case THIS_CHARACTER:
        return "This character and its variants (" + count + ")";
      case SAME_NAME:
        return "Everyone called \"" + npcName + "\" (" + count + ")";
      case THIS_NPC:
      default:
        return "Only this NPC (1)";
    }
  }

  NpcVoiceScope selected() {
    for (Map.Entry<NpcVoiceScope, JRadioButton> entry : buttons.entrySet()) {
      if (entry.getValue().isSelected()) {
        return entry.getKey();
      }
    }
    return NpcVoiceScope.THIS_NPC;
  }

  void select(NpcVoiceScope scope) {
    buttons.get(scope).setSelected(true);
  }

  List<Integer> selectedIds() {
    return ids.getOrDefault(selected(), Collections.emptyList());
  }

  JRadioButton button(NpcVoiceScope scope) {
    return buttons.get(scope);
  }
}
