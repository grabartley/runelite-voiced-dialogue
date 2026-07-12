package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import okhttp3.OkHttpClient;
import org.junit.Test;

public class PreparedBackendTest {

  private static final class TestConfig implements VoicedDialogueConfig {
    SpokenLanguage language = SpokenLanguage.FRENCH;
    int creativity = 3;
    int pace = 120;

    @Override
    public SpokenLanguage cloudLanguage() {
      return language;
    }

    @Override
    public int dialogueCreativity() {
      return creativity;
    }

    @Override
    public int speakingPace() {
      return pace;
    }
  }

  @Test
  public void openRouterSnapshotsDeliverySettingsBeforeQueueing() {
    TestConfig config = new TestConfig();
    OpenRouterTtsBackend backend = new OpenRouterTtsBackend(new OkHttpClient(), config, new Gson());

    SynthesisRequest prepared = backend.prepare(request());
    config.language = VoicedDialogueConfig.SpokenLanguage.ENGLISH;
    config.creativity = 0;
    config.pace = 50;

    assertEquals("French", prepared.preparedLanguage());
    assertEquals("fr-FR", prepared.preparedLanguageCode());
    assertEquals(3, prepared.preparedCreativity());
    assertEquals(120, prepared.preparedSpeedPercent());
    assertTrue(backend.cacheVariant(prepared).contains("creative-v2-3"));
  }

  @Test
  public void aiStudioSnapshotsDeliverySettingsBeforeQueueing() {
    TestConfig config = new TestConfig();
    GeminiAiStudioTtsBackend backend =
        new GeminiAiStudioTtsBackend(new OkHttpClient(), config, new Gson());

    SynthesisRequest prepared = backend.prepare(request());
    config.language = VoicedDialogueConfig.SpokenLanguage.ENGLISH;
    config.creativity = 0;
    config.pace = 50;

    assertEquals("French", prepared.preparedLanguage());
    assertEquals("fr-FR", prepared.preparedLanguageCode());
    assertEquals(3, prepared.preparedCreativity());
    assertEquals(120, prepared.preparedSpeedPercent());
    assertTrue(backend.cacheVariant(prepared).contains("creative-v2-3"));
  }

  private static SynthesisRequest request() {
    return new SynthesisRequest(
        "Hello", VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE), Emotion.NEUTRAL);
  }
}
