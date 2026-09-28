package com.grahambartley.runelite.voiced.dialogue.panel;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
final class ChatheadImages {

  static final String WIKI_FILE_PATH = "https://oldschool.runescape.wiki/w/Special:FilePath";

  private static final String USER_AGENT = "runelite-voiced-dialogue";

  private final OkHttpClient httpClient;
  private final HttpUrl filePath;
  private final ExecutorService executor;
  private final Consumer<Runnable> uiThread;

  private final Map<String, BufferedImage> loaded = new HashMap<>();
  private final Map<String, List<Consumer<BufferedImage>>> pending = new HashMap<>();
  private final Map<String, ImageIcon> icons = new HashMap<>();

  ChatheadImages(
      OkHttpClient httpClient,
      String filePath,
      ExecutorService executor,
      Consumer<Runnable> uiThread) {
    this.httpClient = httpClient;
    this.filePath = HttpUrl.get(filePath);
    this.executor = executor;
    this.uiThread = uiThread;
  }

  ImageIcon placeholder(int size) {
    return icons.computeIfAbsent(
        "placeholder:" + size, k -> new ImageIcon(PanelIcons.chatheadPlaceholder(size)));
  }

  void load(String npcName, int size, Consumer<ImageIcon> onIcon) {
    String file = fileName(npcName);
    String iconKey = file + ":" + size;
    ImageIcon cached = icons.get(iconKey);
    if (cached != null) {
      onIcon.accept(cached);
      return;
    }
    Consumer<BufferedImage> whenLoaded =
        image -> {
          if (image != null) {
            onIcon.accept(icons.computeIfAbsent(iconKey, k -> new ImageIcon(fit(image, size))));
          }
        };
    if (loaded.containsKey(file)) {
      BufferedImage image = loaded.get(file);
      if (image == null) {
        onIcon.accept(placeholder(size));
      } else {
        whenLoaded.accept(image);
      }
      return;
    }
    onIcon.accept(placeholder(size));
    List<Consumer<BufferedImage>> waiting = pending.get(file);
    if (waiting != null) {
      waiting.add(whenLoaded);
      return;
    }
    waiting = new ArrayList<>();
    waiting.add(whenLoaded);
    pending.put(file, waiting);
    try {
      executor.execute(() -> deliver(file, fetch(file)));
    } catch (RejectedExecutionException e) {
      pending.remove(file);
    }
  }

  private void deliver(String file, BufferedImage image) {
    uiThread.accept(
        () -> {
          loaded.put(file, image);
          List<Consumer<BufferedImage>> waiting = pending.remove(file);
          if (waiting != null) {
            waiting.forEach(callback -> callback.accept(image));
          }
        });
  }

  private BufferedImage fetch(String file) {
    HttpUrl url = filePath.newBuilder().addPathSegment(file).build();
    Request request =
        new Request.Builder().url(url).addHeader("User-Agent", USER_AGENT).get().build();
    try (Response response = httpClient.newCall(request).execute()) {
      ResponseBody body = response.body();
      if (!response.isSuccessful() || body == null) {
        return null;
      }
      try (InputStream stream = body.byteStream()) {
        return ImageIO.read(stream);
      }
    } catch (Exception e) {
      log.debug("Chathead {} could not be loaded: {}", file, e.getMessage());
      return null;
    }
  }

  static String fileName(String npcName) {
    String trimmed = npcName.trim().replace(' ', '_');
    if (trimmed.isEmpty()) {
      return "_chathead.png";
    }
    return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1) + "_chathead.png";
  }

  static BufferedImage fit(BufferedImage image, int size) {
    BufferedImage scaled = ImageUtil.resizeImage(image, size, size, true);
    return ImageUtil.resizeCanvas(scaled, size, size);
  }
}
