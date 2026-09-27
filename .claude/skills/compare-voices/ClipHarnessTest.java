package com.grahambartley.runelite.voiced.dialogue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.ResolvedSpeaker;
import com.grahambartley.runelite.voiced.dialogue.profile.Speaker;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceManager;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import com.grahambartley.runelite.voiced.dialogue.speech.aistudio.AiStudioTtsBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.openrouter.OpenRouterTtsBackend;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import okhttp3.OkHttpClient;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.Answers;
import org.slf4j.LoggerFactory;

public class ClipHarnessTest {

  @Test
  public void run() throws Exception {
    File spec = new File("clip-harness.json");
    Assume.assumeTrue(spec.exists());
    JsonObject job;
    try (Reader r = new InputStreamReader(new FileInputStream(spec), StandardCharsets.UTF_8)) {
      job = new JsonParser().parse(r).getAsJsonObject();
    }
    Properties props = new Properties();
    try (FileInputStream in = new FileInputStream(job.get("keyFile").getAsString())) {
      props.load(in);
    }
    boolean aiStudio = job.has("provider") && job.get("provider").getAsString().equals("aistudio");
    String playerAccent = job.has("playerAccent") ? job.get("playerAccent").getAsString() : null;

    VoicedDialogueConfig config = mock(VoicedDialogueConfig.class, Answers.CALLS_REAL_METHODS);
    when(config.openRouterApiKey())
        .thenReturn(props.getProperty("voicedDialogue.openRouterApiKey"));
    when(config.googleAiStudioApiKey())
        .thenReturn(props.getProperty("voicedDialogue.googleAiStudioApiKey"));
    when(config.ttsProvider())
        .thenReturn(
            aiStudio
                ? VoicedDialogueConfig.TtsProvider.GOOGLE_AI_STUDIO
                : VoicedDialogueConfig.TtsProvider.OPENROUTER);
    when(config.debugMode()).thenReturn(true);
    if (playerAccent != null) {
      when(config.playerAccent()).thenReturn(playerAccent);
    }

    VoiceManager vm = VoiceManager.create(config, mock(Client.class));
    File out = new File(job.get("outDir").getAsString());
    out.mkdirs();
    boolean render = job.get("mode").getAsString().equals("render");

    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    logs.start();
    root.addAppender(logs);

    SynthesisBackend backend;
    if (aiStudio) {
      AiStudioTtsBackend ai = new AiStudioTtsBackend(new OkHttpClient(), config, new Gson());
      ai.setNotice(n -> System.out.println("NOTICE " + n));
      backend = ai;
    } else {
      OpenRouterTtsBackend or = new OpenRouterTtsBackend(new OkHttpClient(), config, new Gson());
      or.setNotice(n -> System.out.println("NOTICE " + n));
      backend = or;
    }

    JsonArray results = new JsonArray();
    for (JsonElement el : job.getAsJsonArray("cases")) {
      JsonObject c = el.getAsJsonObject();
      if (c.has("missing")) {
        continue;
      }
      try {
        results.add(renderCase(c, config, vm, backend, logs, out, render));
      } catch (Exception e) {
        JsonObject failed = new JsonObject();
        failed.addProperty("key", c.get("key").getAsString());
        failed.addProperty("error", e.toString());
        results.add(failed);
      }
      System.out.println("CLIP " + c.get("key").getAsString());
    }
    Gson pretty = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    Files.write(
        new File(out, "results.json").toPath(),
        pretty.toJson(results).getBytes(StandardCharsets.UTF_8));
  }

  private static JsonObject renderCase(
      JsonObject c,
      VoicedDialogueConfig config,
      VoiceManager vm,
      SynthesisBackend backend,
      ListAppender<ILoggingEvent> logs,
      File out,
      boolean render)
      throws Exception {
    String kind = c.get("kind").getAsString();
    logs.list.clear();
    ResolvedSpeaker resolved;
    boolean player = false;
    if (kind.equals("player")) {
      when(config.playerVoice())
          .thenReturn(
              c.get("gender").getAsString().equals("MALE")
                  ? VoiceManager.PlayerVoice.TYPE_A
                  : VoiceManager.PlayerVoice.TYPE_B);
      resolved = vm.resolve(Speaker.PLAYER, null);
      player = true;
    } else if (kind.equals("narrator")) {
      resolved = vm.resolveNarrator();
    } else {
      int id = c.get("id").getAsInt();
      int baseId = c.has("baseId") ? c.get("baseId").getAsInt() : id;
      String name = c.get("name").getAsString();
      NPC npc = mock(NPC.class);
      NPCComposition comp = mock(NPCComposition.class);
      when(comp.getId()).thenReturn(baseId);
      when(comp.getName()).thenReturn(name);
      when(npc.getId()).thenReturn(id);
      when(npc.getName()).thenReturn(name);
      when(npc.getComposition()).thenReturn(comp);
      resolved = vm.resolveNpc(npc);
    }
    Emotion emotion =
        c.has("emotion") ? Emotion.valueOf(c.get("emotion").getAsString()) : Emotion.NEUTRAL;
    JsonObject res = new JsonObject();
    res.addProperty("key", c.get("key").getAsString());
    res.addProperty("accent", resolved.profile().accent());
    res.addProperty("profileName", resolved.profile().name());
    res.addProperty("spec", String.valueOf(resolved.voice()));
    res.addProperty("voice", voiceId(resolved));
    if (render) {
      SynthesisRequest req =
          new SynthesisRequest(
              c.get("line").getAsString(),
              resolved.voice(),
              emotion,
              resolved.profile(),
              false,
              player);
      Pcm pcm = backend.synthesize(req);
      if (pcm != null) {
        writeWav(pcm, new File(out, c.get("key").getAsString() + ".wav"));
        res.addProperty("seconds", pcm.getSamples().length / (double) pcm.getSampleRate());
      } else {
        res.addProperty("error", "no audio");
      }
    }
    JsonArray trace = new JsonArray();
    for (ILoggingEvent e : logs.list) {
      String m = e.getFormattedMessage();
      if (m.contains("[TTS")) {
        trace.add(m);
      }
    }
    res.add("trace", trace);
    return res;
  }

  private static String voiceId(ResolvedSpeaker resolved) throws Exception {
    Object model =
        Class.forName("com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiTtsModel")
            .getConstructor()
            .newInstance();
    for (java.lang.reflect.Method m : model.getClass().getMethods()) {
      if (m.getName().equals("voiceFor") && m.getParameterCount() == 2) {
        return String.valueOf(m.invoke(model, resolved.voice(), resolved.profile()));
      }
    }
    return String.valueOf(
        model
            .getClass()
            .getMethod("voiceFor", resolved.voice().getClass())
            .invoke(model, resolved.voice()));
  }

  private static void writeWav(Pcm pcm, File file) throws Exception {
    float[] s = pcm.getSamples();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(s.length * 2);
    for (float f : s) {
      int v = Math.max(-32768, Math.min(32767, Math.round(f * 32767f)));
      bytes.write(v & 0xff);
      bytes.write((v >> 8) & 0xff);
    }
    AudioFormat fmt = new AudioFormat(pcm.getSampleRate(), 16, 1, true, false);
    byte[] data = bytes.toByteArray();
    try (AudioInputStream ais =
        new AudioInputStream(new ByteArrayInputStream(data), fmt, s.length)) {
      javax.sound.sampled.AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
    }
  }
}
