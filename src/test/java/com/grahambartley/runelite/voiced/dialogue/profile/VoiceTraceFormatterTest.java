package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import org.junit.Test;

public class VoiceTraceFormatterTest {

  @Test
  public void buildNpcTraceShowsWorldHitSourceAndSeed() {
    String trace =
        VoiceTraceFormatter.buildNpcTrace(
            "Goblin", 101, NpcRace.GOBLIN, NpcGender.MALE, false, "table-hit", 24);
    assertTrue(trace, trace.contains("npc='Goblin'"));
    assertTrue(trace, trace.contains("world=HIT(id=101)"));
    assertTrue(trace, trace.contains("race=GOBLIN"));
    assertTrue(trace, trace.contains("lifeStage=adult"));
    assertTrue(trace, trace.contains("source=table-hit"));
    assertTrue(trace, trace.contains("seed=24"));
  }

  @Test
  public void buildNpcTraceShowsWorldMissForUntabledNpc() {
    String trace =
        VoiceTraceFormatter.buildNpcTrace(
            "Hans", null, NpcRace.UNKNOWN, NpcGender.UNKNOWN, false, "not-in-world", 26);
    assertTrue(trace, trace.contains("world=MISS"));
    assertTrue(trace, trace.contains("race=UNKNOWN"));
    assertTrue(trace, trace.contains("seed=26"));
  }

  @Test
  public void buildResolvedLineGivesTheWholeDecisionInOneRecord() {
    String line =
        VoiceTraceFormatter.buildResolvedLine(
            "cloud-openrouter",
            VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 26),
            "Hans",
            "HAPPY",
            "Hans",
            "British");
    assertTrue(line, line.startsWith("[TTS line]"));
    assertTrue(line, line.contains("backend=cloud-openrouter"));
    assertTrue(line, line.contains("kind=npc"));
    assertTrue(line, line.contains("name='Hans'"));
    assertTrue(line, line.contains("emotion=HAPPY"));
    assertTrue(line, line.contains("race=HUMAN"));
    assertTrue(line, line.contains("gender=MALE"));
    assertTrue(line, line.contains("lifeStage=adult"));
    assertTrue(line, line.contains("seed=26"));
    assertTrue(line, line.contains("profile='Hans'"));
    assertTrue(line, line.contains("accent='British'"));
  }

  @Test
  public void buildResolvedLineCollapsesAbsentSeedAndProfileToDash() {
    String line =
        VoiceTraceFormatter.buildResolvedLine(
            "cloud-openrouter", VoiceSpec.player(NpcGender.FEMALE), null, "NEUTRAL", null, null);
    assertTrue(line, line.contains("kind=player"));
    assertTrue(line, line.contains("name=-"));
    assertTrue(line, line.contains("seed=-"));
    assertTrue(line, line.contains("profile=-"));
    assertTrue(line, line.contains("accent=-"));
  }

  @Test
  public void buildNpcTraceShowsChildAge() {
    String trace =
        VoiceTraceFormatter.buildNpcTrace(
            "Shilop", 3501, NpcRace.HUMAN, NpcGender.MALE, true, "table-hit", 12);
    assertTrue(trace, trace.contains("lifeStage=child"));
  }

  @Test
  public void buildResolvedLineShowsChildAge() {
    String line =
        VoiceTraceFormatter.buildResolvedLine(
            "cloud-openrouter",
            VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 12, true),
            "Shilop",
            "HAPPY",
            "Shilop",
            "British");
    assertTrue(line, line.contains("lifeStage=child"));
  }

  @Test
  public void buildResolvedLineMarksNarrationAsItsOwnKindWithNoCharacterFields() {
    String line =
        VoiceTraceFormatter.buildResolvedLine(
            "cloud-openrouter", VoiceSpec.NARRATOR, null, "NEUTRAL", "Narrator", "British");
    assertTrue(line, line.contains("kind=narrator"));
    assertTrue("narration must never log a literal null name: " + line, line.contains("name=-"));
    assertTrue(line, line.contains("race=-"));
    assertTrue(line, line.contains("gender=-"));
    assertTrue(line, line.contains("lifeStage=-"));
    assertTrue(line, line.contains("seed=-"));
    assertTrue(line, line.contains("profile='Narrator'"));
  }

  @Test
  public void buildPlayerTraceShowsGender() {
    String trace = VoiceTraceFormatter.buildPlayerTrace(NpcGender.FEMALE);
    assertTrue(trace, trace.contains("player ->"));
    assertTrue(trace, trace.contains("gender=FEMALE"));
  }
}
