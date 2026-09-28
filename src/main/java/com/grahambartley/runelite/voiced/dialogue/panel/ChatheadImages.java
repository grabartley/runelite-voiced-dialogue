package com.grahambartley.runelite.voiced.dialogue.panel;

import com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

  private static final int NOT_FOUND = 404;

  static final String WIKI_FILE_PATH = "https://oldschool.runescape.wiki/w/Special:FilePath";

  private final OkHttpClient httpClient;
  private final HttpUrl filePath;
  private final ExecutorService executor;
  private final Consumer<Runnable> uiThread;

  private final Map<String, BufferedImage> loaded = new HashMap<>();
  private final Map<String, List<Consumer<BufferedImage>>> pending = new HashMap<>();
  private final Map<String, ImageIcon> icons = new HashMap<>();
  private final Set<String> wanted = ConcurrentHashMap.newKeySet();

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

  void newBatch() {
    wanted.clear();
  }

  void load(String npcName, int size, Consumer<ImageIcon> onIcon) {
    String file = fileName(npcName);
    wanted.add(file);
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
    submit(file);
  }

  private void submit(String file) {
    try {
      executor.execute(() -> fetchIfWanted(file));
    } catch (RejectedExecutionException e) {
      pending.remove(file);
    }
  }

  private void fetchIfWanted(String file) {
    if (!wanted.contains(file)) {
      uiThread.accept(() -> skipped(file));
      return;
    }
    try {
      BufferedImage image = fetch(file);
      uiThread.accept(() -> deliver(file, image));
    } catch (IOException | RuntimeException e) {
      log.debug("Chathead {} could not be reached: {}", file, e.getMessage());
      uiThread.accept(() -> pending.remove(file));
    }
  }

  private void skipped(String file) {
    if (wanted.contains(file)) {
      submit(file);
    } else {
      pending.remove(file);
    }
  }

  private void deliver(String file, BufferedImage image) {
    loaded.put(file, image);
    List<Consumer<BufferedImage>> waiting = pending.remove(file);
    if (waiting != null) {
      waiting.forEach(callback -> callback.accept(image));
    }
  }

  private BufferedImage fetch(String file) throws IOException {
    HttpUrl url = filePath.newBuilder().addPathSegment(file).build();
    Request request =
        new Request.Builder().url(url).addHeader("User-Agent", CloudHttp.USER_AGENT).get().build();
    try (Response response = httpClient.newCall(request).execute()) {
      if (response.code() == NOT_FOUND) {
        return null;
      }
      ResponseBody body = response.body();
      if (!response.isSuccessful() || body == null) {
        throw new IOException("wiki answered " + response.code());
      }
      try (InputStream stream = body.byteStream()) {
        return ImageIO.read(stream);
      }
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
