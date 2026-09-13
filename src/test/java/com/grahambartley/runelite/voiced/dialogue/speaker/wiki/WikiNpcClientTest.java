package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.speaker.AttributeSource;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WikiNpcClientTest {

  private MockWebServer server;
  private WikiNpcClient client;

  @Before
  public void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client = new WikiNpcClient(new OkHttpClient(), server.url("/api.php").toString());
  }

  @After
  public void tearDown() throws Exception {
    server.shutdown();
  }

  private static String pageBody(String infobox, String... categories) {
    String content = infobox.replace("\n", "\\n").replace("\"", "\\\"");
    StringBuilder categoryJson = new StringBuilder();
    for (String category : categories) {
      categoryJson.append(categoryJson.length() == 0 ? "" : ",");
      categoryJson.append("{\"title\":\"").append(category).append("\"}");
    }
    return "{\"query\":{\"pages\":[{\"title\":\"X\",\"categories\":["
        + categoryJson
        + "],\"revisions\":[{\"slots\":{\"main\":{\"content\":\""
        + content
        + "\"}}}]}]}}";
  }

  private void enqueue(String infobox, String... categories) {
    server.enqueue(new MockResponse().setBody(pageBody(infobox, categories)));
  }

  @Test
  public void parsesRaceGenderAndDesertEthnicity() {
    enqueue(
        "{{Infobox NPC\n|race = [[Human]]\n|gender = Female\n|leagueRegion = Desert\n"
            + "|location = Pollnivneach\n|id = 123\n}}");

    NpcAttributes attributes = client.lookup(123, "Some Trader").attributes();
    assertEquals("Human", attributes.getRace());
    assertEquals("Female", attributes.getGender());
    assertEquals("kharidian", attributes.getEthnicity());
    assertEquals(AttributeSource.WIKI, attributes.getSource());
    assertEquals(123, attributes.getNpcId());
  }

  @Test
  public void eachVersionOfASwitchInfoboxLearnsItsOwnGender() {
    enqueue(
        "{{Infobox NPC\n|race = [[Human]]\n|gender1 = Male\n|gender2 = Female\n"
            + "|id1 = 3010\n|id2 = 3012\n|leagueRegion = Asgarnia\n}}");

    assertEquals("Female", client.lookup(3012, "Guard").attributes().getGender());
  }

  @Test
  public void menaphiteCategoryMapsToTheEgyptianEthnicity() {
    enqueue(
        "{{Infobox NPC\n|race=[[Human]]\n|gender=Male\n|leagueRegion=Desert\n|id=1\n}}",
        "Category:Menaphites");

    assertEquals("menaphite", client.lookup(1, "Menaphite Thug").attributes().getEthnicity());
  }

  @Test
  public void aMonsterPageTakesItsRaceFromTheCategories() {
    enqueue(
        "{{Infobox Monster\n|gender = Female\n|leagueRegion = Fremennik\n|id = 55\n}}",
        "Category:Trolls");

    NpcAttributes attributes = client.lookup(55, "Kob").attributes();
    assertEquals("Troll", attributes.getRace());
    assertEquals("Female", attributes.getGender());
    assertEquals("fremennik", attributes.getEthnicity());
  }

  @Test
  public void aPageWithNoRaceAnywhereStillLearnsGenderAndEthnicity() {
    enqueue("{{Infobox NPC\n|gender = Female\n|leagueRegion = Kandarin\n|id = 7\n}}");

    NpcAttributes attributes = client.lookup(7, "Someone").attributes();
    assertEquals("Human", attributes.getRace());
    assertEquals("Female", attributes.getGender());
    assertEquals("kandarin", attributes.getEthnicity());
  }

  @Test
  public void mapsLoreRaceOntoVoiceBucket() {
    enqueue("{{Infobox NPC\n|race=[[Ogre]]\n|gender=Male\n|id=1\n}}");
    assertEquals(
        "an ogre voices from the Troll bucket",
        "Troll",
        client.lookup(1, "Ogre").attributes().getRace());
  }

  @Test
  public void aPageThatIsNotAnNpcIsUndocumented() {
    enqueue("{{Infobox Item\n|name = Bucket\n}}");

    WikiLookup lookup = client.lookup(1, "Bucket");
    assertTrue("a page the wiki holds is answered, not retried", lookup.isUndocumented());
    assertFalse(lookup.isUnreachable());
    assertNull(lookup.attributes());
  }

  @Test
  public void aMissingPageIsUndocumented() {
    server.enqueue(new MockResponse().setBody("{\"query\":{\"pages\":[{\"missing\":true}]}}"));
    assertTrue(client.lookup(1, "Not An NPC").isUndocumented());
  }

  @Test
  public void anErrorLeavesTheNpcUnanswered() {
    server.enqueue(new MockResponse().setResponseCode(HTTP_INTERNAL_ERROR));

    WikiLookup lookup = client.lookup(1, "Anything");
    assertTrue("a wiki outage must not blacklist the npc", lookup.isUnreachable());
    assertFalse(lookup.isUndocumented());
  }

  @Test
  public void aBlankNameIsNotLookedUp() {
    assertTrue(client.lookup(1, " ").isUndocumented());
    assertTrue(client.lookup(1, null).isUndocumented());
    assertTrue(client.lookup(1, "<col=00ffff></col>").isUndocumented());
    assertEquals(0, server.getRequestCount());
  }

  @Test
  public void theNameIsNormalisedBeforeItReachesTheWiki() throws Exception {
    enqueue("{{Infobox NPC\n|race=Human\n|gender=Male\n|id=1\n}}");

    client.lookup(1, "<col=00ffff>Hans</col> ");

    assertTrue(
        "markup and padding never reach the query",
        server.takeRequest().getPath().contains("titles=Hans"));
  }

  @Test
  public void aSuccessfulResponseTheApiCouldNotAnswerIsUndocumented() {
    server.enqueue(new MockResponse().setBody("{\"error\":{\"code\":\"invalidtitle\"}}"));

    assertTrue(client.lookup(1, "?").isUndocumented());
  }

  @Test
  public void theCategoriesAreRequestedAlongsideTheRevision() throws Exception {
    enqueue("{{Infobox NPC\n|race=Human\n|gender=Male\n|id=1\n}}");
    client.lookup(1, "Someone");

    String path = server.takeRequest().getPath();
    assertEquals("categories are requested", true, path.contains("revisions%7Ccategories"));
  }
}
