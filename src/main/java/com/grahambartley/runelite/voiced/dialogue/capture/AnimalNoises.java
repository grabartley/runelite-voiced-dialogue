package com.grahambartley.runelite.voiced.dialogue.capture;

import java.util.regex.Pattern;

public final class AnimalNoises {

  private static final Pattern SEPARATOR = Pattern.compile("[\\s\\p{Punct}&&[^-]]+");

  private static final Pattern NOISE =
      Pattern.compile(
          "ba+|bah+|maa+|meh+|moo+|mooo+|quack+|cluck+|cock-?a-?doodle-?doo|squeak+|squeal+"
              + "|woof+|bark+|arf+|yap+|howl+|gr+|grr+|growl+|snarl+|roar+|meow+|miaow+|mew+"
              + "|purr+|hiss+|s+|oink+|snort+|neigh+|whinny+|bray+|hee-?haw|tweet+|chirp+|caw+"
              + "|squawk+|screech+|hoot+|ook+|eek+|ook-?ook|ribbit+|croak+|buzz+|chitter+"
              + "|click+|whistle+|bleat+|trumpet+|bellow+|yowl+|whine+|wuff+|rawr+|grunt+",
          Pattern.CASE_INSENSITIVE);

  private AnimalNoises() {}

  public static boolean isNothingButNoise(String line) {
    if (line == null || line.isEmpty()) {
      return false;
    }
    boolean sawWord = false;
    for (String token : SEPARATOR.split(line)) {
      if (token.isEmpty()) {
        continue;
      }
      sawWord = true;
      if (!NOISE.matcher(token).matches()) {
        return false;
      }
    }
    return sawWord;
  }
}
