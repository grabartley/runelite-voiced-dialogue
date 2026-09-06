package com.grahambartley.runelite.voiced.dialogue.voice;

/**
 * Which of the two speaker classes a line belongs to. Picks the resolution path in {@link
 * VoiceManager} and the speaker class the synthesis request is marked with. Purely in-process: it
 * reaches no cache key, request payload, or persisted value.
 */
public enum Speaker {
  PLAYER,
  NPC
}
