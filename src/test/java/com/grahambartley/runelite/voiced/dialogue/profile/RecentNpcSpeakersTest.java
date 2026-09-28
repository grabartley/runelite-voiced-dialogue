package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class RecentNpcSpeakersTest {

  private final RecentNpcSpeakers speakers = new RecentNpcSpeakers();

  @Test
  public void startsEmpty() {
    assertTrue(speakers.newestFirst().isEmpty());
  }

  @Test
  public void listsTheNewestSpeakerFirst() {
    speakers.record(1, "Hans");
    speakers.record(2, "Bob");

    assertEquals(
        Arrays.asList(new HeardNpc(2, "Bob"), new HeardNpc(1, "Hans")), speakers.newestFirst());
  }

  @Test
  public void hearingSomeoneAgainMovesThemToTheFront() {
    speakers.record(1, "Hans");
    speakers.record(2, "Bob");
    speakers.record(1, "Hans");

    assertEquals(
        Arrays.asList(new HeardNpc(1, "Hans"), new HeardNpc(2, "Bob")), speakers.newestFirst());
  }

  @Test
  public void keepsOnlyTheMostRecentSpeakers() {
    for (int id = 0; id < RecentNpcSpeakers.CAPACITY + 5; id++) {
      speakers.record(id, "Npc " + id);
    }

    assertEquals(RecentNpcSpeakers.CAPACITY, speakers.newestFirst().size());
    assertEquals(
        new HeardNpc(RecentNpcSpeakers.CAPACITY + 4, "Npc " + (RecentNpcSpeakers.CAPACITY + 4)),
        speakers.newestFirst().get(0));
  }

  @Test
  public void blankNamesAreIgnored() {
    speakers.record(1, null);
    speakers.record(2, "  ");

    assertTrue(speakers.newestFirst().isEmpty());
  }

  @Test
  public void namesAreTrimmed() {
    speakers.record(1, " Hans ");

    assertEquals(new HeardNpc(1, "Hans"), speakers.newestFirst().get(0));
  }

  @Test
  public void theListenerHearsANewSpeakerButNotTheSameOneTwiceInARow() {
    AtomicInteger calls = new AtomicInteger();
    speakers.setListener(calls::incrementAndGet);

    speakers.record(1, "Hans");
    speakers.record(1, "Hans");
    speakers.record(2, "Bob");

    assertEquals(2, calls.get());
  }

  @Test
  public void aNullListenerIsSafe() {
    speakers.setListener(null);
    speakers.record(1, "Hans");

    assertEquals(1, speakers.newestFirst().size());
  }
}
