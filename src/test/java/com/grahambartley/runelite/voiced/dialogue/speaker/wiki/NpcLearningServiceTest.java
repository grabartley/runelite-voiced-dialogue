package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.grahambartley.runelite.voiced.dialogue.speaker.LearnedNpcStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NpcLearningServiceTest {

  private static final Executor INLINE = Runnable::run;
  private static final WikiCallThrottle UNTHROTTLED = new WikiCallThrottle(0);

  private final Gson gson = new Gson();

  private MockWebServer server;
  private WikiNpcClient client;
  private LearnedNpcStore store;
  private Path file;

  @Before
  public void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client = new WikiNpcClient(new OkHttpClient(), server.url("/api.php").toString());
    file = Files.createTempDirectory("learn").resolve("learned-npcs.json");
    store = new LearnedNpcStore(file, gson);
  }

  @After
  public void tearDown() throws Exception {
    server.shutdown();
  }

  private NpcLearningService service(boolean enabled) {
    return new NpcLearningService(client, store, INLINE, () -> enabled, UNTHROTTLED);
  }

  private void enqueueNpc() {
    server.enqueue(
        new MockResponse()
            .setBody(
                "{\"query\":{\"pages\":[{\"revisions\":[{\"slots\":{\"main\":{\"content\":"
                    + "\"{{Infobox NPC\\n|race=[[Troll]]\\n|gender=Male\\n|id=1\\n}}\"}}}]}]}}"));
  }

  private void enqueueMiss() {
    server.enqueue(new MockResponse().setBody("{\"query\":{\"pages\":[{\"missing\":true}]}}"));
  }

  @Test
  public void learnsAnUnknownNpcThenDoesNotQueryAgain() {
    enqueueNpc();
    NpcLearningService service = service(true);

    service.considerLearning(500, "New Troll");
    assertNotNull("the wiki hit is stored", store.get(500));
    assertEquals("Troll", store.get(500).getRace());
    assertEquals(1, server.getRequestCount());

    service.considerLearning(500, "New Troll");
    assertEquals("an id is attempted at most once", 1, server.getRequestCount());
  }

  @Test
  public void disabledMakesNoRequest() {
    service(false).considerLearning(600, "Someone");
    assertEquals(0, server.getRequestCount());
    assertNull(store.get(600));
  }

  @Test
  public void alreadyLearnedIdIsNotRefetched() {
    store.learn(700, "Human", "Female", null);
    service(true).considerLearning(700, "Known");
    assertEquals(0, server.getRequestCount());
  }

  @Test
  public void aMissIsRememberedSoTheNextSessionDoesNotRepeatIt() throws Exception {
    enqueueMiss();
    service(true).considerLearning(800, "Nobody");
    assertEquals(1, server.getRequestCount());

    LearnedNpcStore reloaded = new LearnedNpcStore(file, gson);
    assertFalse(reloaded.isWorthLooking(800, System.currentTimeMillis()));
    new NpcLearningService(client, reloaded, INLINE, () -> true, UNTHROTTLED)
        .considerLearning(800, "Nobody");
    assertEquals("a remembered miss is not queried again", 1, server.getRequestCount());
  }

  @Test
  public void aStaleMissIsRetried() throws Exception {
    enqueueMiss();
    service(true).considerLearning(900, "Nobody");

    String aged =
        new String(Files.readAllBytes(file), "UTF-8").replaceAll("\"900\":\\d+", "\"900\":1");
    Files.write(file, aged.getBytes("UTF-8"));

    LearnedNpcStore reloaded = new LearnedNpcStore(file, gson);
    assertTrue(reloaded.isWorthLooking(900, System.currentTimeMillis()));
    enqueueNpc();
    new NpcLearningService(client, reloaded, INLINE, () -> true, UNTHROTTLED)
        .considerLearning(900, "New Troll");
    assertEquals(2, server.getRequestCount());
    assertEquals("Troll", reloaded.get(900).getRace());
  }

  @Test
  public void onlyDialogueMenuOptionsStartALookup() {
    assertTrue(NpcLearningService.isDialogueOption("Talk-to"));
    assertTrue(NpcLearningService.isDialogueOption("talk to"));
    assertFalse(NpcLearningService.isDialogueOption("Attack"));
    assertFalse(NpcLearningService.isDialogueOption("Examine"));
    assertFalse(NpcLearningService.isDialogueOption(null));
  }
}
