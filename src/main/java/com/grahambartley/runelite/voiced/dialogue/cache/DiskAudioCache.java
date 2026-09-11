package com.grahambartley.runelite.voiced.dialogue.cache;

import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiskAudioCache {

  private static final int MAGIC = 0x54_44_43_31;

  private static final int HEADER_BYTES = 16;

  public static final long DEFAULT_MAX_BYTES = 256L * 1024 * 1024;

  public static final long UNLIMITED = 0;

  private final Path dir;
  private final LongSupplier maxBytes;

  private volatile boolean disabled;

  public DiskAudioCache(Path dir) {
    this(dir, DEFAULT_MAX_BYTES);
  }

  public DiskAudioCache(Path dir, long maxBytes) {
    this(dir, () -> maxBytes);
  }

  public DiskAudioCache(Path dir, LongSupplier maxBytes) {
    this.dir = dir;
    this.maxBytes = maxBytes;
  }

  public Pcm get(String backendId, String voiceKey, Emotion emotion, String text) {
    if (disabled) {
      return null;
    }
    Path file;
    try {
      file = fileFor(backendId, voiceKey, emotion, text);
    } catch (RuntimeException e) {
      return null;
    }
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try {
      Pcm pcm = decode(Files.readAllBytes(file));
      if (pcm == null) {
        deleteQuietly(file);
        return null;
      }
      return pcm;
    } catch (IOException | RuntimeException e) {
      log.debug("Disk cache read failed for {}; treating as miss", file.getFileName(), e);
      deleteQuietly(file);
      return null;
    }
  }

  public void put(String backendId, String voiceKey, Emotion emotion, String text, Pcm pcm) {
    if (disabled || pcm == null || pcm.getSamples() == null) {
      return;
    }
    Path file;
    try {
      ensureDir();
      file = fileFor(backendId, voiceKey, emotion, text);
    } catch (IOException | RuntimeException e) {
      log.debug("Disk cache unavailable; disabling on-disk caching", e);
      disabled = true;
      return;
    }
    Path tmp = null;
    try {
      tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
      Files.write(tmp, encode(pcm));
      try {
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException atomicUnsupported) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
      tmp = null;
      enforceSizeCap();
    } catch (IOException | RuntimeException e) {
      log.debug("Disk cache write failed for {}; skipping", file.getFileName(), e);
    } finally {
      if (tmp != null) {
        deleteQuietly(tmp);
      }
    }
  }

  private Path fileFor(String backendId, String voiceKey, Emotion emotion, String text) {
    return dir.resolve(hashKey(backendId, voiceKey, emotion, text) + ".tdc");
  }

  private static String hashKey(String backendId, String voiceKey, Emotion emotion, String text) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      feed(md, backendId);
      feed(md, voiceKey);
      feed(md, emotion == null ? "null" : emotion.name());
      feed(md, text);
      byte[] digest = md.digest();
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16));
        sb.append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static void feed(MessageDigest md, String field) {
    byte[] bytes = field == null ? new byte[0] : field.getBytes(StandardCharsets.UTF_8);
    ByteBuffer len = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.length);
    md.update(len.array());
    md.update(bytes);
  }

  private static byte[] encode(Pcm pcm) {
    float[] samples = pcm.getSamples();
    ByteBuffer buf =
        ByteBuffer.allocate(HEADER_BYTES + samples.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(MAGIC);
    buf.putInt(pcm.getSampleRate());
    buf.putInt(samples.length);
    buf.putInt(0);
    buf.asFloatBuffer().put(samples);
    return buf.array();
  }

  private static Pcm decode(byte[] bytes) {
    if (bytes.length < HEADER_BYTES) {
      return null;
    }
    ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    if (buf.getInt() != MAGIC) {
      return null;
    }
    int sampleRate = buf.getInt();
    int sampleCount = buf.getInt();
    buf.getInt();
    if (sampleRate <= 0 || sampleCount < 0) {
      return null;
    }
    if (bytes.length != HEADER_BYTES + (long) sampleCount * 4) {
      return null;
    }
    float[] samples = new float[sampleCount];
    buf.asFloatBuffer().get(samples);
    return new Pcm(samples, sampleRate);
  }

  private void ensureDir() throws IOException {
    if (!Files.isDirectory(dir)) {
      Files.createDirectories(dir);
    }
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
    }
  }

  private void enforceSizeCap() {
    long cap = maxBytes.getAsLong();
    if (cap <= UNLIMITED) {
      return;
    }
    List<Entry> entries = new ArrayList<>();
    long total = 0;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.tdc")) {
      for (Path p : stream) {
        try {
          BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
          if (attrs.isRegularFile()) {
            entries.add(new Entry(p, attrs.size(), attrs.lastModifiedTime().toMillis()));
            total += attrs.size();
          }
        } catch (IOException ignored) {
        }
      }
    } catch (IOException e) {
      log.debug("Disk cache size scan failed; skipping eviction this round", e);
      return;
    }
    if (total <= cap) {
      return;
    }
    entries.sort(Comparator.comparingLong(e -> e.mtime));
    for (Entry e : entries) {
      if (total <= cap) {
        break;
      }
      deleteQuietly(e.path);
      total -= e.size;
    }
  }

  private static final class Entry {
    final Path path;
    final long size;
    final long mtime;

    Entry(Path path, long size, long mtime) {
      this.path = path;
      this.size = size;
      this.mtime = mtime;
    }
  }
}
