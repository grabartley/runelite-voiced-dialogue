package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.profile.ResolvedSpeaker;
import com.grahambartley.runelite.voiced.dialogue.profile.Speaker;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceManager;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speech.BackendProvider;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import java.util.List;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Warms the cache for the visible dialogue options: each non-header, non-blank option is built into
 * the same player request the dispatcher would produce and handed to the prefetcher. Gated by the
 * prefetch toggle and backend availability.
 */
public class DialoguePrefetchCoordinatorTest {

  private final VoiceManager voiceManager = mock(VoiceManager.class);
  private final DialoguePrefetcher prefetcher = mock(DialoguePrefetcher.class);
  private final BackendProvider backendProvider = mock(BackendProvider.class);
  private final SynthesisBackend backend = mock(SynthesisBackend.class);
  private final VoicedDialogueConfig config = mock(VoicedDialogueConfig.class);

  private final DialoguePrefetchCoordinator coordinator =
      new DialoguePrefetchCoordinator(
          voiceManager,
          new DialogueTextCleaner(new ProfanityFilter()),
          prefetcher,
          backendProvider,
          config);

  @Before
  public void setUp() {
    when(backendProvider.active()).thenReturn(backend);
  }

  @Test
  public void doesNothingWhenPrefetchDisabled() {
    when(config.prefetch()).thenReturn(false);
    coordinator.prefetchOptions(mock(Widget.class));
    verify(prefetcher, never()).offer(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  public void doesNothingWhenBackendUnavailable() {
    when(config.prefetch()).thenReturn(true);
    when(backend.isAvailable()).thenReturn(false);
    coordinator.prefetchOptions(mock(Widget.class));
    verify(prefetcher, never()).offer(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  public void offersOnlyRealOptionsSkippingHeaderBlankAndNull() {
    when(config.prefetch()).thenReturn(true);
    when(backend.isAvailable()).thenReturn(true);
    when(voiceManager.resolve(Speaker.PLAYER, null))
        .thenReturn(new ResolvedSpeaker(mock(VoiceSpec.class), null));

    Widget[] children = {
      option("Select an Option"), option("Yes, I'll help."), null, option(""), option("No thanks.")
    };
    Widget options = mock(Widget.class);
    when(options.getDynamicChildren()).thenReturn(children);

    coordinator.prefetchOptions(options);

    ArgumentCaptor<List<SynthesisRequest>> captor = ArgumentCaptor.forClass(List.class);
    verify(prefetcher).offer(captor.capture());
    List<SynthesisRequest> offered = captor.getValue();
    assertEquals(2, offered.size());
    assertEquals("Yes, I'll help.", offered.get(0).text());
    assertEquals("No thanks.", offered.get(1).text());
  }

  @Test
  public void everyOfferedLineIsMarkedSpeculativeSoSpendReadsAsWarming() {
    when(config.prefetch()).thenReturn(true);
    when(backend.isAvailable()).thenReturn(true);
    when(voiceManager.resolve(Speaker.PLAYER, null))
        .thenReturn(new ResolvedSpeaker(mock(VoiceSpec.class), null));

    Widget[] children = {option("Yes, I'll help."), option("No thanks.")};
    Widget options = mock(Widget.class);
    when(options.getDynamicChildren()).thenReturn(children);

    coordinator.prefetchOptions(options);

    ArgumentCaptor<List<SynthesisRequest>> captor = ArgumentCaptor.forClass(List.class);
    verify(prefetcher).offer(captor.capture());
    for (SynthesisRequest offered : captor.getValue()) {
      assertTrue(
          "a warmed option is speculative, not a line the player heard: " + offered.text(),
          offered.prefetch());
      assertTrue("it is still the player's own line", offered.player());
    }
  }

  @Test
  public void anUnchangedOptionMenuIsNotRebuiltOnEveryTick() {
    when(config.prefetch()).thenReturn(true);
    when(backend.isAvailable()).thenReturn(true);
    when(voiceManager.resolve(Speaker.PLAYER, null))
        .thenReturn(new ResolvedSpeaker(mock(VoiceSpec.class), null));
    Widget options = menu("Yes, I'll help.", "No thanks.");

    coordinator.prefetchOptions(options);
    coordinator.prefetchOptions(options);
    coordinator.prefetchOptions(options);

    verify(prefetcher, times(1)).offer(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  public void aChangedOptionMenuIsRebuilt() {
    when(config.prefetch()).thenReturn(true);
    when(backend.isAvailable()).thenReturn(true);
    when(voiceManager.resolve(Speaker.PLAYER, null))
        .thenReturn(new ResolvedSpeaker(mock(VoiceSpec.class), null));

    coordinator.prefetchOptions(menu("Yes, I'll help.", "No thanks."));
    coordinator.prefetchOptions(menu("Tell me more.", "Goodbye."));

    verify(prefetcher, times(2)).offer(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  public void resetLetsTheSameMenuWarmAgainInTheNextConversation() {
    when(config.prefetch()).thenReturn(true);
    when(backend.isAvailable()).thenReturn(true);
    when(voiceManager.resolve(Speaker.PLAYER, null))
        .thenReturn(new ResolvedSpeaker(mock(VoiceSpec.class), null));

    coordinator.prefetchOptions(menu("Yes, I'll help.", "No thanks."));
    coordinator.reset();
    coordinator.prefetchOptions(menu("Yes, I'll help.", "No thanks."));

    verify(prefetcher).reset();
    verify(prefetcher, times(2)).offer(org.mockito.ArgumentMatchers.anyList());
  }

  private static Widget menu(String... texts) {
    Widget[] children = new Widget[texts.length];
    for (int i = 0; i < texts.length; i++) {
      children[i] = option(texts[i]);
    }
    Widget options = mock(Widget.class);
    when(options.getDynamicChildren()).thenReturn(children);
    return options;
  }

  private static Widget option(String text) {
    Widget w = mock(Widget.class);
    when(w.getText()).thenReturn(text);
    return w;
  }
}
