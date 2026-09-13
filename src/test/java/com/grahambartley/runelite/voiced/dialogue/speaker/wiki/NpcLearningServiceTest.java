package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.grahambartley.runelite.voiced.dialogue.speaker.LearnedNpcStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.IntPredicate;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
    return service(enabled, npcId -> false);
  }

  private NpcLearningService service(boolean enabled, IntPredicate voiced) {
    return new NpcLearningService(client, store, INLINE, () -> enabled, voiced, UNTHROTTLED);
  }

  private void enqueueNpc() {
    server.enqueue(npcResponse());
  }

  private static MockResponse npcResponse() {
    return new MockResponse()
        .setBody(
            "{\"query\":{\"pages\":[{\"revisions\":[{\"slots\":{\"main\":{\"content\":"
                + "\"{{Infobox NPC\\n|race=[[Troll]]\\n|gender=Male\\n|id=1\\n}}\"}}}]}]}}");
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
    service(true, npcId -> store.get(npcId) != null).considerLearning(700, "Known");
    assertEquals(0, server.getRequestCount());
  }

  @Test
  public void aMissIsRememberedSoTheNextSessionDoesNotRepeatIt() throws Exception {
    enqueueMiss();
    service(true).considerLearning(800, "Nobody");
    assertEquals(1, server.getRequestCount());

    LearnedNpcStore reloaded = new LearnedNpcStore(file, gson);
    assertFalse(reloaded.isPastMissWindow(800, System.currentTimeMillis()));
    new NpcLearningService(client, reloaded, INLINE, () -> true, npcId -> false, UNTHROTTLED)
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
    assertTrue(reloaded.isPastMissWindow(900, System.currentTimeMillis()));
    enqueueNpc();
    new NpcLearningService(client, reloaded, INLINE, () -> true, npcId -> false, UNTHROTTLED)
        .considerLearning(900, "New Troll");
    assertEquals(2, server.getRequestCount());
    assertEquals("Troll", reloaded.get(900).getRace());
  }

  @Test
  public void aRunOfUnreachableAnswersStopsAskingForAWhile() {
    for (int call = 0; call < 3; call++) {
      server.enqueue(new MockResponse().setResponseCode(HTTP_UNAVAILABLE));
    }
    NpcLearningService service = service(true);

    service.considerLearning(1200, "Someone");
    service.considerLearning(1201, "Someone Else");
    service.considerLearning(1202, "A Third");
    assertEquals(3, server.getRequestCount());

    service.considerLearning(1203, "A Fourth");
    assertEquals("a wiki that keeps failing is left alone", 3, server.getRequestCount());
  }

  @Test
  public void onlyDialogueMenuOptionsStartAConversation() {
    NpcLearningService service = service(true);

    assertTrue(service.startsConversation("Talk-to"));
    assertTrue(service.startsConversation("talk to"));
    assertFalse(service.startsConversation("Attack"));
    assertFalse(service.startsConversation("Examine"));
    assertFalse(service.startsConversation(null));
  }

  @Test
  public void anNpcTheBundledTableAlreadyVoicesIsNeverLookedUp() {
    service(true, npcId -> npcId == 1100).considerLearning(1100, "Bundled NPC");

    assertEquals("the bundled table wins, so nothing is asked", 0, server.getRequestCount());
    assertNull(store.get(1100));
  }

  @Test
  public void aClosedServiceNeverQueuesAnotherLookup() {
    NpcLearningService service = service(true);
    service.close();

    service.considerLearning(1, "Troll");

    assertEquals("a closed service makes no wiki call", 0, server.getRequestCount());
    assertNull("a closed service learns nothing", store.get(1));
  }

  @Test
  public void aQueuedLookupIsDroppedWhenTheServiceClosesBeforeItRuns() {
    DeferredExecutor executor = new DeferredExecutor();
    NpcLearningService service =
        new NpcLearningService(client, store, executor, () -> true, npcId -> false, UNTHROTTLED);

    service.considerLearning(1, "Troll");
    service.close();
    executor.runAll();

    assertEquals("a lookup queued at close makes no wiki call", 0, server.getRequestCount());
    assertNull("a lookup queued at close learns nothing", store.get(1));
  }

  @Test
  public void aLookupAnsweredAfterTheServiceClosesNeverWritesToTheStore() {
    NpcLearningService[] holder = new NpcLearningService[1];
    server.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            holder[0].close();
            return npcResponse();
          }
        });
    NpcLearningService service =
        new NpcLearningService(client, store, INLINE, () -> true, npcId -> false, UNTHROTTLED);
    holder[0] = service;

    service.considerLearning(1, "Troll");

    assertEquals("the lookup still went out", 1, server.getRequestCount());
    assertNull("a lookup answered after close is discarded", store.get(1));
  }

  @Test
  public void aServiceClosedDuringItsThrottleWaitNeverCallsTheWiki() {
    DeferredExecutor executor = new DeferredExecutor();
    WikiCallThrottle throttle = new WikiCallThrottle(50);
    throttle.awaitTurn();
    NpcLearningService service =
        new NpcLearningService(client, store, executor, () -> true, npcId -> false, throttle);

    service.considerLearning(1, "Troll");
    service.close();
    executor.runAll();

    assertEquals("a service closed mid-wait makes no wiki call", 0, server.getRequestCount());
    assertNull("and learns nothing", store.get(1));
  }

  private static final class DeferredExecutor implements Executor {
    private final List<Runnable> tasks = new ArrayList<>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    void runAll() {
      List<Runnable> snapshot = new ArrayList<>(tasks);
      tasks.clear();
      for (Runnable task : snapshot) {
        task.run();
      }
    }
  }
}
