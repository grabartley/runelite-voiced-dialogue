package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.mockito.Mockito.mock;

import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceCatalog;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceImportPlan;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverrideStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import net.runelite.client.config.ConfigManager;
import okhttp3.OkHttpClient;

final class PanelFixtures {

  static final int GUARD = 11911;
  static final int GUARD_CAPTAIN = 11917;
  static final int FALADOR_GUARD = 3094;
  static final int HANS = 3105;
  static final int BOB = 2812;

  private PanelFixtures() {}

  static NpcVoiceCatalog catalog() {
    Map<Integer, String> names = new HashMap<>();
    Map<Integer, String> symbols = new HashMap<>();
    String[] guardSymbols = {
      "FAI_VARROCK_GUARD02",
      "FAI_VARROCK_GUARD02_VARIANT01",
      "FAI_VARROCK_GUARD02_VARIANT02",
      "FAI_VARROCK_GUARD02_F",
      "FAI_VARROCK_GUARD02_F_VARIANT01",
      "FAI_VARROCK_GUARD02_F_VARIANT02",
      "FAI_VARROCK_GUARD_CAPTAIN02"
    };
    for (int i = 0; i < guardSymbols.length; i++) {
      names.put(GUARD + i, "Guard");
      symbols.put(GUARD + i, guardSymbols[i]);
    }
    names.put(FALADOR_GUARD, "Guard");
    names.put(HANS, "Hans");
    names.put(BOB, "Bob");
    return new NpcVoiceCatalog(names, symbols);
  }

  static NpcVoiceOverrideStore store() {
    return new NpcVoiceOverrideStore(mock(ConfigManager.class));
  }

  static ChatheadImages offlineChatheads() {
    return new ChatheadImages(
        new OkHttpClient(), ChatheadImages.WIKI_FILE_PATH, new RejectingExecutor(), Runnable::run);
  }

  static NpcListEntry entry(String name, List<Integer> ids, boolean heard, int preferredId) {
    return new NpcListEntry(name, ids, heard, false, preferredId);
  }

  static NpcListEntry single(String name, int id) {
    return entry(name, Collections.singletonList(id), false, id);
  }

  static final class ScriptedDialogs implements NpcVoiceTransfer.Dialogs {
    final List<String> copied = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    final List<String> summaries = new ArrayList<>();
    Path exportFile;
    Path importFile;
    String pasted;
    NpcVoiceImportPlan.Mode mode;

    @Override
    public void copyToClipboard(String text) {
      copied.add(text);
    }

    @Override
    public Path chooseExportFile() {
      return exportFile;
    }

    @Override
    public String pasteImport() {
      return pasted;
    }

    @Override
    public Path chooseImportFile() {
      return importFile;
    }

    @Override
    public NpcVoiceImportPlan.Mode confirmImport(String summary) {
      summaries.add(summary);
      return mode;
    }

    @Override
    public void info(String message) {
      infos.add(message);
    }

    @Override
    public void error(String message) {
      errors.add(message);
    }
  }

  static final class RejectingExecutor extends AbstractExecutorService {
    @Override
    public void execute(Runnable command) {
      throw new RejectedExecutionException("offline");
    }

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
      return true;
    }

    @Override
    public boolean isTerminated() {
      return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }
  }
}
