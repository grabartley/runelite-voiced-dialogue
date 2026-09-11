package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.speaker.AttributeSource;
import com.grahambartley.runelite.voiced.dialogue.speaker.LifeStage;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcLearningService;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import org.junit.Test;

/**
 * A resolved NPC identity to a {@link VoiceSpec}, including detection-failure fallbacks and
 * learning.
 */
public class NpcVoiceResolverTest {

  private final VoicedDialogueConfig config = mock(VoicedDialogueConfig.class);
  private final NpcVoiceResolver resolver = new NpcVoiceResolver(config);

  @Test
  public void blankNameResolvesToDefaultHumanMale() {
    assertDefaultHumanMale(resolver.resolve("", identity(null, null, false)));
  }

  @Test
  public void npcNotInWorldResolvesToDefaultHumanMale() {
    assertDefaultHumanMale(resolver.resolve("Hans", identity(null, null, false)));
  }

  @Test
  public void analysisFailureResolvesToDefaultHumanMale() {
    assertDefaultHumanMale(resolver.resolve("Hans", identity(5, null, false)));
  }

  @Test
  public void detectedNpcCarriesItsRaceAndGender() {
    NpcLearningService learning = mock(NpcLearningService.class);
    resolver.setLearningService(learning);

    VoiceSpec spec =
        resolver.resolve(
            "Goblin",
            identity(101, attributes("Goblin", "Male", AttributeSource.STATIC_TABLE), false));

    assertEquals(NpcRace.GOBLIN, spec.race());
    assertEquals(NpcGender.MALE, spec.gender());
    assertFalse(spec.player());
    verify(learning, never()).considerLearning(101, "Goblin");
  }

  @Test
  public void unknownRaceVoicesAsHumanAndTriggersLearning() {
    NpcLearningService learning = mock(NpcLearningService.class);
    resolver.setLearningService(learning);

    VoiceSpec spec =
        resolver.resolve(
            "Merfolk",
            identity(202, attributes("Merfolk", "Female", AttributeSource.LEARNED), false));

    assertEquals("an unrecognised race voices as human", NpcRace.HUMAN, spec.race());
    assertEquals(NpcGender.FEMALE, spec.gender());
    verify(learning).considerLearning(202, "Merfolk");
  }

  @Test
  public void anUndetectedGenderVoicesAsTheDefaultMale() {
    VoiceSpec spec =
        resolver.resolve(
            "Nulgar", identity(303, attributes("Human", null, AttributeSource.LEARNED), false));

    assertEquals(NpcGender.MALE, spec.gender());
  }

  @Test
  public void tableLifeStageMarkerFlagsTheSpecAsChild() {
    NpcAttributes attrs = attributes("Human", "Male", AttributeSource.STATIC_TABLE);
    attrs.setLifeStage(LifeStage.CHILD);

    VoiceSpec spec = resolver.resolve("Shilop", identity(3501, attrs, false));

    assertTrue("the table life-stage marker makes a child spec", spec.child());
    assertEquals(NpcRace.HUMAN, spec.race());
    assertEquals(NpcGender.MALE, spec.gender());
  }

  @Test
  public void childNamedNpcFlagsTheSpecAsChildWithoutATableMarker() {
    VoiceSpec spec =
        resolver.resolve(
            "Schoolboy",
            identity(1919, attributes("Human", "Male", AttributeSource.STATIC_TABLE), true));

    assertTrue("a child-name keyword match makes a child spec", spec.child());
  }

  @Test
  public void childNamedNpcStaysAChildEvenWhenDetectionFails() {
    VoiceSpec spec = resolver.resolve("Child", identity(null, null, true));

    assertTrue("the default fallback keeps the child flag from the name", spec.child());
    assertEquals(NpcRace.HUMAN, spec.race());
    assertEquals(NpcGender.MALE, spec.gender());
  }

  @Test
  public void adultsResolveWithoutTheChildFlag() {
    VoiceSpec spec =
        resolver.resolve(
            "Hans",
            identity(3105, attributes("Human", "Male", AttributeSource.STATIC_TABLE), false));

    assertFalse(spec.child());
  }

  private void assertDefaultHumanMale(VoiceSpec spec) {
    assertEquals(NpcRace.HUMAN, spec.race());
    assertEquals(NpcGender.MALE, spec.gender());
    assertFalse("default voice is not a player spec", spec.player());
    assertTrue("default voice still gets a per-NPC variety seed", spec.hasVoiceSeed());
  }

  private static NpcIdentity identity(Integer worldId, NpcAttributes attributes, boolean child) {
    NpcProfileTable.NameMatch nameMatch = mock(NpcProfileTable.NameMatch.class);
    when(nameMatch.child()).thenReturn(child);
    return new NpcIdentity(worldId, attributes, nameMatch);
  }

  private static NpcAttributes attributes(String race, String gender, String source) {
    return new NpcAttributes(race, gender, source);
  }
}
