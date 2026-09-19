package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import java.lang.reflect.Field;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
public final class FollowerDialogReader {

  static final String DIALOG_CLASS = "com.follower.speech.FollowerDialog";

  static final int RESCAN_TICKS = 50;

  private final OverlayManager overlayManager;
  private final String dialogClass;

  private Object dialog;
  private Field openField;
  private Field pagesField;
  private Field pageField;
  private Field nodeField;
  private Field playerSpeakingField;
  private boolean unreadable;
  private int ticksUntilRescan;

  public FollowerDialogReader(OverlayManager overlayManager) {
    this(overlayManager, DIALOG_CLASS);
  }

  FollowerDialogReader(OverlayManager overlayManager, String dialogClass) {
    this.overlayManager = overlayManager;
    this.dialogClass = dialogClass;
  }

  public FollowerDialogLine currentLine() {
    if (unreadable || !locate()) {
      return null;
    }
    try {
      if (!openField.getBoolean(dialog)) {
        return null;
      }
      String[] pages = (String[]) pagesField.get(dialog);
      int page = pageField.getInt(dialog);
      if (pages == null || page < 0 || page >= pages.length) {
        return null;
      }
      String text = pages[page];
      if (text == null || text.isEmpty()) {
        return null;
      }
      Object node = nodeField.get(dialog);
      boolean playerSpeaking = node != null && playerSpeakingField.getBoolean(node);
      return new FollowerDialogLine(text, page, playerSpeaking);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      giveUp(e.toString());
      return null;
    }
  }

  private boolean locate() {
    if (dialog != null) {
      return true;
    }
    if (ticksUntilRescan > 0) {
      ticksUntilRescan--;
      return false;
    }
    ticksUntilRescan = RESCAN_TICKS;
    overlayManager.anyMatch(this::bindIfDialog);
    return dialog != null;
  }

  private boolean bindIfDialog(Overlay overlay) {
    return overlay != null && dialogClass.equals(overlay.getClass().getName()) && bindTo(overlay);
  }

  private boolean bindTo(Overlay overlay) {
    try {
      Class<?> type = overlay.getClass();
      openField = readable(type, "open");
      pagesField = readable(type, "pages");
      pageField = readable(type, "page");
      nodeField = readable(type, "node");
      playerSpeakingField = readable(nodeField.getType(), "playerSpeaking");
      dialog = overlay;
      log.debug("[TTS follower] reading the Talk-to dialog of {}", dialogClass);
      return true;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      giveUp(e.toString());
      return false;
    }
  }

  private static Field readable(Class<?> type, String name) throws ReflectiveOperationException {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private void giveUp(String reason) {
    unreadable = true;
    dialog = null;
    log.info("[TTS follower] the Talk-to dialog stays silent, its layout has moved: {}", reason);
  }
}
