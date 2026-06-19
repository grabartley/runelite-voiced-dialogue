package com.grahambartley.synthesis;

import com.grahambartley.synthesis.engine.EngineInstaller;
import com.grahambartley.synthesis.engine.ExternalEngineClient;
import com.grahambartley.tts.Pcm;
import java.nio.file.Path;
import java.util.EnumSet;
import lombok.extern.slf4j.Slf4j;

/**
 * The local Kokoro engine exposed as a {@link SynthesisBackend}, backed by an external {@code
 * --stdio} process instead of an in-JVM model.
 *
 * <p>This is the default backend and the universal fallback. On {@link #warmUp()} it installs the
 * per-OS engine bundle (download + sha256 verify + extract, all off the game thread on the pipeline
 * thread) via {@link EngineInstaller} and spawns the child process via {@link
 * ExternalEngineClient}. Each {@link #synthesize} writes the request to the engine and decodes the
 * returned PCM frame, which carries the engine-reported sample rate so playback never pitch-shifts.
 * The voice mapping is carried as {@code race}/{@code gender}/{@code player} on the wire; the
 * engine's {@code SpeakerMatrix} (kept in lockstep with the plugin's {@code VoiceProfile} by the
 * #36 drift test) turns it into a concrete speaker id.
 *
 * <p>Kokoro is deliberately neutral-only ({@link Emotion#NEUTRAL}); emotional delivery is reserved
 * for the GPU and cloud backends, so this backend advertises only neutral and {@link
 * BackendProvider} downgrades anything else before it reaches here. When the engine cannot be
 * installed (no release published yet, unsupported platform, download/verify failure) {@link
 * #isAvailable()} reports {@code false} after warm-up so {@link BackendProvider} falls back and
 * surfaces a one-time notice instead of crashing.
 */
@Slf4j
public final class LocalKokoroBackend implements SynthesisBackend {

  /** Installs/resolves the engine bundle for this OS. */
  private final EngineInstaller installer;

  /** Builds the transport client once the launcher is resolved (seam for tests). */
  private final ClientFactory clientFactory;

  private volatile ExternalEngineClient client;
  private volatile boolean installAttempted;

  /** Seam so tests can supply a fake transport without spawning a real process. */
  public interface ClientFactory {
    ExternalEngineClient create(Path launcher);
  }

  public LocalKokoroBackend(EngineInstaller installer, ClientFactory clientFactory) {
    this.installer = installer;
    this.clientFactory = clientFactory;
  }

  @Override
  public String id() {
    return BackendProvider.LOCAL_KOKORO_ID;
  }

  /**
   * Available optimistically until warm-up has run (so the provider does not fall back before the
   * pipeline thread has had a chance to install/spawn the engine off the game thread), then
   * reflects the real child-process health. After a failed install/spawn this returns {@code false}
   * so the provider falls back with a one-time notice.
   */
  @Override
  public boolean isAvailable() {
    ExternalEngineClient c = client;
    if (c != null) {
      return c.isHealthy();
    }
    // Before warm-up: assume installable. After a warm-up that produced no client: unavailable.
    return !installAttempted;
  }

  @Override
  public EnumSet<Emotion> supportedEmotions() {
    return EnumSet.of(Emotion.NEUTRAL);
  }

  @Override
  public Pcm synthesize(SynthesisRequest request) {
    ExternalEngineClient c = client;
    if (c == null) {
      // First use before warm-up completed: bring the engine up now. This runs on the pipeline
      // thread (never the game thread), so the blocking install/spawn is safe here.
      warmUp();
      c = client;
      if (c == null) {
        return null;
      }
    }
    return c.synthesize(request);
  }

  @Override
  public synchronized void warmUp() {
    if (client != null || installAttempted) {
      return;
    }
    installAttempted = true;
    EngineInstaller.Installed installed = installer.install();
    if (installed == null) {
      log.info(
          "Local Kokoro engine is not installed; backend unavailable, provider will fall back.");
      return;
    }
    ExternalEngineClient c = clientFactory.create(installed.launcher());
    try {
      c.start();
      this.client = c;
      log.info("Local Kokoro engine warmed up: {}", installed.launcher());
    } catch (Exception e) {
      log.warn("Local Kokoro engine failed to start: {}", e.getMessage());
      try {
        c.stop();
      } catch (RuntimeException ignored) {
        // best-effort
      }
    }
  }

  @Override
  public synchronized void close() {
    ExternalEngineClient c = client;
    if (c != null) {
      c.stop();
      client = null;
    }
  }
}
