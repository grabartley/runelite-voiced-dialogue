package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ChatheadImagesTest {

  private final MockWebServer server = new MockWebServer();
  private final List<Runnable> uiQueue = new ArrayList<>();
  private ExecutorService executor;
  private ChatheadImages images;

  @Before
  public void setUp() throws Exception {
    server.start();
    executor = Executors.newSingleThreadExecutor();
    images =
        new ChatheadImages(
            new OkHttpClient(),
            server.url("/w/Special:FilePath").toString(),
            executor,
            uiQueue::add);
  }

  @After
  public void tearDown() throws Exception {
    executor.shutdown();
    server.shutdown();
  }

  private static byte[] png(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(width / 2, height / 2, Color.RED.getRGB());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private void drain() throws Exception {
    executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
    new ArrayList<>(uiQueue).forEach(Runnable::run);
    uiQueue.clear();
  }

  private static MockResponse pngResponse(byte[] bytes) {
    return new MockResponse().setResponseCode(200).setBody(new Buffer().write(bytes));
  }

  @Test
  public void fileNameFollowsTheWikiChatheadConvention() {
    assertEquals("Hans_chathead.png", ChatheadImages.fileName("Hans"));
    assertEquals("Duke_Horacio_chathead.png", ChatheadImages.fileName(" Duke Horacio "));
    assertEquals("Man_chathead.png", ChatheadImages.fileName("man"));
  }

  @Test
  public void thePlaceholderShowsFirstThenTheChathead() throws Exception {
    server.enqueue(pngResponse(png(60, 40)));
    List<ImageIcon> shown = new ArrayList<>();

    images.load("Hans", 32, shown::add);
    drain();

    assertEquals(2, shown.size());
    assertSame(images.placeholder(32), shown.get(0));
    assertEquals(32, shown.get(1).getIconWidth());
    assertEquals(32, shown.get(1).getIconHeight());
    RecordedRequest request = server.takeRequest();
    assertEquals("/w/Special:FilePath/Hans_chathead.png", request.getPath());
    assertEquals("runelite-voiced-dialogue", request.getHeader("User-Agent"));
  }

  @Test
  public void aLoadedChatheadIsServedFromMemory() throws Exception {
    server.enqueue(pngResponse(png(40, 40)));
    images.load("Hans", 32, icon -> {});
    drain();

    List<ImageIcon> shown = new ArrayList<>();
    images.load("Hans", 32, shown::add);

    assertEquals(1, shown.size());
    assertNotSame(images.placeholder(32), shown.get(0));
    assertEquals(1, server.getRequestCount());
  }

  @Test
  public void anotherSizeReusesTheDownloadedImage() throws Exception {
    server.enqueue(pngResponse(png(40, 40)));
    images.load("Hans", 32, icon -> {});
    drain();

    List<ImageIcon> shown = new ArrayList<>();
    images.load("Hans", 48, shown::add);

    assertEquals(1, server.getRequestCount());
    assertEquals(1, shown.size());
    assertNotSame(images.placeholder(48), shown.get(0));
    assertEquals(48, shown.get(0).getIconWidth());
  }

  @Test
  public void concurrentRequestsForOneNpcShareOneDownload() throws Exception {
    server.enqueue(pngResponse(png(40, 40)));
    List<ImageIcon> first = new ArrayList<>();
    List<ImageIcon> second = new ArrayList<>();

    images.load("Hans", 32, first::add);
    images.load("Hans", 32, second::add);
    drain();

    assertEquals(1, server.getRequestCount());
    assertEquals(2, first.size());
    assertEquals(2, second.size());
  }

  @Test
  public void aMissingChatheadKeepsThePlaceholderAndIsNotRetried() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(404));
    List<ImageIcon> shown = new ArrayList<>();

    images.load("Nobody", 32, shown::add);
    drain();
    images.load("Nobody", 32, shown::add);
    drain();

    assertEquals(2, shown.size());
    assertTrue(shown.stream().allMatch(icon -> icon == images.placeholder(32)));
    images.load("Nobody", 48, shown::add);
    assertSame(images.placeholder(48), shown.get(2));
    assertEquals(1, server.getRequestCount());
  }

  @Test
  public void anUnreadableBodyKeepsThePlaceholder() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("not an image"));
    List<ImageIcon> shown = new ArrayList<>();

    images.load("Hans", 32, shown::add);
    drain();

    assertEquals(1, shown.size());
  }

  @Test
  public void aStoppedExecutorLeavesThePlaceholder() {
    executor.shutdown();
    List<ImageIcon> shown = new ArrayList<>();

    images.load("Hans", 32, shown::add);

    assertEquals(1, shown.size());
    assertSame(images.placeholder(32), shown.get(0));
  }

  @Test
  public void fitKeepsTheAspectRatioOnASquareCanvas() {
    BufferedImage wide = new BufferedImage(80, 40, BufferedImage.TYPE_INT_ARGB);

    BufferedImage fitted = ChatheadImages.fit(wide, 32);

    assertEquals(32, fitted.getWidth());
    assertEquals(32, fitted.getHeight());
  }

  @Test
  public void aNameDroppedByANewBatchBeforeItsTurnIsNotFetched() throws Exception {
    List<ImageIcon> shown = new ArrayList<>();
    CountDownLatch release = new CountDownLatch(1);
    executor.execute(
        () -> {
          try {
            release.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            throw new IllegalStateException(e);
          }
        });

    images.load("Hans", 32, shown::add);
    images.newBatch();
    release.countDown();
    drain();

    assertEquals(0, server.getRequestCount());
    server.enqueue(pngResponse(png(40, 40)));
    images.load("Hans", 32, shown::add);
    drain();
    assertEquals(1, server.getRequestCount());
    assertNotSame(images.placeholder(32), shown.get(shown.size() - 1));
  }

  @Test
  public void aNetworkFailureIsRetriedOnTheNextLoad() throws Exception {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server.enqueue(pngResponse(png(40, 40)));
    List<ImageIcon> shown = new ArrayList<>();

    images.load("Hans", 32, shown::add);
    drain();
    assertEquals(1, shown.size());

    images.load("Hans", 32, shown::add);
    drain();

    assertEquals(3, shown.size());
    assertNotSame(images.placeholder(32), shown.get(2));
  }
}
