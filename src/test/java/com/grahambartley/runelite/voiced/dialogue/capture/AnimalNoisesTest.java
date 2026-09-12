package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnimalNoisesTest {

  private static final String[] NOISES = {
    "Baa", "Baa!", "Baaaa", "Baa baa", "Quack", "Quack!", "Quack quack quack", "Moo", "Moooo.",
    "Cluck cluck", "Squeak!", "Woof woof", "Grrr", "Meow", "Hiss!", "Oink", "Neigh", "Tweet",
    "Squawk!", "Ook ook", "Ribbit", "baa", "MOO", "Cock-a-doodle-doo", "Hee-haw", "Bleat",
  };

  private static final String[] SPEECH = {
    "Hear ye, hear ye!",
    "The cow says moo",
    "Baa, said the sheep to the farmer",
    "Buy my wares",
    "Moo over there and buy a cowbell",
    "Grrr, I will have my revenge",
    "Join the H.A.M.",
    "Alright bruv",
  };

  @Test
  public void anAnimalNoiseIsNotSomethingToVoice() {
    for (String line : NOISES) {
      assertTrue(line + " should read as noise", AnimalNoises.isNothingButNoise(line));
    }
  }

  @Test
  public void realSpeechIsAlwaysVoicedEvenWhenItMentionsANoise() {
    for (String line : SPEECH) {
      assertFalse(line + " should read as speech", AnimalNoises.isNothingButNoise(line));
    }
  }

  @Test
  public void anEmptyOrAbsentLineIsNotTreatedAsNoise() {
    assertFalse(AnimalNoises.isNothingButNoise(null));
    assertFalse(AnimalNoises.isNothingButNoise(""));
    assertFalse(AnimalNoises.isNothingButNoise("!!!"));
  }
}
