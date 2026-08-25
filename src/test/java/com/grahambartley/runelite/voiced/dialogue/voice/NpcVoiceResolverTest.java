package com.grahambartley.runelite.voiced.dialogue.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.data.NPCAttributes;
import com.grahambartley.runelite.voiced.dialogue.data.NPCDemographicAnalyzer;
import com.grahambartley.runelite.voiced.dialogue.data.NpcLearningService;
import com.grahambartley.runelite.voiced.dialogue.synthesis.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.voice.VoiceManager.NPCGender;
import com.grahambartley.runelite.voiced.dialogue.voice.VoiceManager.NPCRace;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.NPC;
import org.junit.Test;

/** NPC name to {@link VoiceSpec} resolution, including detection-failure fallbacks and learning. */
public class NpcVoiceResolverTest {

  private final VoicedDialogueConfig config = mock(VoicedDialogueConfig.class);
  private final NPCDemographicAnalyzer analyzer = mock(NPCDemographicAnalyzer.class);
  private final NpcFinder finder = mock(NpcFinder.class);
  private final Set<String> childNames = new HashSet<>();
  private final NpcVoiceResolver resolver =
      new NpcVoiceResolver(config, analyzer, finder, childNames::contains);

  @Test
  public void blankNameResolvesToDefaultHumanMale() {
    VoiceSpec spec = resolver.resolve("");
    assertDefaultHumanMale(spec);
  }

  @Test
  public void npcNotInWorldResolvesToDefaultHumanMale() {
    when(finder.findByName("Hans")).thenReturn(null);
    assertDefaultHumanMale(resolver.resolve("Hans"));
  }

  @Test
  public void analysisFailureResolvesToDefaultHumanMale() {
    NPC npc = mock(NPC.class);
    when(npc.getId()).thenReturn(5);
    when(finder.findByName("Hans")).thenReturn(npc);
    when(analyzer.analyzeNPC(npc)).thenReturn(null);
    assertDefaultHumanMale(resolver.resolve("Hans"));
  }

  @Test
  public void detectedNpcCarriesItsRaceAndGender() {
    NPC npc = mock(NPC.class);
    when(npc.getId()).thenReturn(101);
    when(finder.findByName("Goblin")).thenReturn(npc);
    NPCAttributes attrs = attributes("Goblin", "Male", "StaticTable");
    when(analyzer.analyzeNPC(npc)).thenReturn(attrs);
    NpcLearningService learning = mock(NpcLearningService.class);
    resolver.setLearningService(learning);

    VoiceSpec spec = resolver.resolve("Goblin");

    assertEquals(NPCRace.GOBLIN, spec.race());
    assertEquals(NPCGender.MALE, spec.gender());
    assertFalse(spec.player());
    verify(learning, never()).considerLearning(101, "Goblin");
  }

  @Test
  public void unknownRaceVoicesAsHumanAndTriggersLearning() {
    NPC npc = mock(NPC.class);
    when(npc.getId()).thenReturn(202);
    when(finder.findByName("Penguin")).thenReturn(npc);
    NPCAttributes attrs = attributes("Penguin", "Female", "learned");
    when(analyzer.analyzeNPC(npc)).thenReturn(attrs);
    NpcLearningService learning = mock(NpcLearningService.class);
    resolver.setLearningService(learning);

    VoiceSpec spec = resolver.resolve("Penguin");

    assertEquals("an unrecognised race voices as human", NPCRace.HUMAN, spec.race());
    assertEquals(NPCGender.FEMALE, spec.gender());
    verify(learning).considerLearning(202, "Penguin");
  }

  @Test
  public void tableLifeStageMarkerFlagsTheSpecAsChild() {
    NPC npc = mock(NPC.class);
    when(npc.getId()).thenReturn(3501);
    when(finder.findByName("Shilop")).thenReturn(npc);
    NPCAttributes attrs = attributes("Human", "Male", "StaticTable");
    when(attrs.isChild()).thenReturn(true);
    when(analyzer.analyzeNPC(npc)).thenReturn(attrs);

    VoiceSpec spec = resolver.resolve("Shilop");

    assertTrue("the table life-stage marker makes a child spec", spec.child());
    assertEquals(NPCRace.HUMAN, spec.race());
    assertEquals(NPCGender.MALE, spec.gender());
  }

  @Test
  public void childNamedNpcFlagsTheSpecAsChildWithoutATableMarker() {
    childNames.add("Schoolboy");
    NPC npc = mock(NPC.class);
    when(npc.getId()).thenReturn(1919);
    when(finder.findByName("Schoolboy")).thenReturn(npc);
    NPCAttributes attrs = attributes("Human", "Male", "StaticTable");
    when(analyzer.analyzeNPC(npc)).thenReturn(attrs);

    assertTrue(
        "a child-name keyword match makes a child spec", resolver.resolve("Schoolboy").child());
  }

  @Test
  public void childNamedNpcStaysAChildEvenWhenDetectionFails() {
    childNames.add("Child");
    when(finder.findByName("Child")).thenReturn(null);

    VoiceSpec spec = resolver.resolve("Child");

    assertTrue("the default fallback keeps the child flag from the name", spec.child());
    assertEquals(NPCRace.HUMAN, spec.race());
    assertEquals(NPCGender.MALE, spec.gender());
  }

  @Test
  public void adultsResolveWithoutTheChildFlag() {
    NPC npc = mock(NPC.class);
    when(npc.getId()).thenReturn(3105);
    when(finder.findByName("Hans")).thenReturn(npc);
    NPCAttributes attrs = attributes("Human", "Male", "StaticTable");
    when(analyzer.analyzeNPC(npc)).thenReturn(attrs);

    assertFalse(resolver.resolve("Hans").child());
  }

  private void assertDefaultHumanMale(VoiceSpec spec) {
    assertEquals(NPCRace.HUMAN, spec.race());
    assertEquals(NPCGender.MALE, spec.gender());
    assertFalse("default voice is not a player spec", spec.player());
    assertTrue("default voice still gets a per-NPC variety seed", spec.hasVoiceSeed());
  }

  private static NPCAttributes attributes(String race, String gender, String source) {
    NPCAttributes attributes = mock(NPCAttributes.class);
    when(attributes.getRace()).thenReturn(race);
    when(attributes.getGender()).thenReturn(gender);
    when(attributes.getSource()).thenReturn(source);
    return attributes;
  }
}
