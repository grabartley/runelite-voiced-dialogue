package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider;
import com.grahambartley.runelite.voiced.dialogue.synthesis.SpendTracker.ProviderSpend;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The {@code ::voicedspend} chat command: what it is called, and how a session's usage reads back
 * to the player.
 *
 * <p>One chat line per provider used. Both carry the same counts (lines voiced, lines prefetched,
 * the translation hop when there was one), but they answer the cost question differently because
 * the providers do:
 *
 * <ul>
 *   <li><b>OpenRouter</b> states what the key has actually spent, so its line quotes a billed
 *       figure. When the balance cannot be read the line says so rather than substituting a guess.
 *   <li><b>Google AI Studio</b> returns no cost at all, so its line reports the audio and text
 *       tokens it really metered and converts them at the published rate, labelled an estimate.
 *       Both models a session can touch are priced: the speech model, and the cheaper translation
 *       model when a non-English language or a speaking style routes lines through it.
 * </ul>
 *
 * <p>The translation hop needs no special handling on the OpenRouter side, since it bills against
 * the same key and is therefore already inside that provider's reported spend.
 */
public final class SpendReport {

  /** The chat command, without the {@code ::} prefix RuneLite strips before dispatching it. */
  public static final String COMMAND = "voicedspend";

  private static final String NOTHING_SPENT =
      "Nothing has been voiced this session, so nothing has been spent.";

  /** Below this the four-decimal figure rounds to zero, which reads as free rather than tiny. */
  private static final double SMALLEST_SHOWN_USD = 0.0001;

  private SpendReport() {}

  /** Whether a {@code CommandExecuted} command name is this plugin's spend command. */
  public static boolean matches(String command) {
    return command != null && COMMAND.equalsIgnoreCase(command.trim());
  }

  /**
   * The chat lines for a snapshot. {@code openRouterSpentUsd} is what OpenRouter reports the key
   * has spent this session, or {@code null} when that balance could not be read.
   */
  public static List<String> lines(List<ProviderSpend> snapshot, Double openRouterSpentUsd) {
    if (snapshot == null || snapshot.isEmpty()) {
      return Collections.singletonList(NOTHING_SPENT);
    }
    List<String> lines = new ArrayList<>(snapshot.size());
    for (ProviderSpend spend : snapshot) {
      lines.add(
          spend.provider() == TtsProvider.GOOGLE_AI_STUDIO
              ? aiStudioLine(spend)
              : openRouterLine(spend, openRouterSpentUsd));
    }
    return lines;
  }

  private static String openRouterLine(ProviderSpend spend, Double spentUsd) {
    StringBuilder text = counts(spend);
    text.append(count(spend.speechCharacters())).append(" characters sent");
    translation(text, spend);
    if (spentUsd == null) {
      return text.append(
              ". OpenRouter's billed spend could not be read; see openrouter.ai/settings/credits.")
          .toString();
    }
    return text.append(". Spent ")
        .append(usd(spentUsd))
        .append(", billed by OpenRouter.")
        .toString();
  }

  private static String aiStudioLine(ProviderSpend spend) {
    StringBuilder text = counts(spend);
    long tokens =
        spend.audioTokens()
            + spend.speechPromptTokens()
            + spend.translationInputTokens()
            + spend.translationOutputTokens();
    if (tokens == 0) {
      // No usageMetadata came back, so there is no measured quantity to cost. Report what is known
      // rather than pricing a zero.
      text.append(count(spend.speechCharacters())).append(" characters sent");
      translation(text, spend);
      return text.append(". Google AI Studio reported no token counts, so cost is unknown.")
          .toString();
    }
    text.append(count(spend.audioTokens()))
        .append(" audio tokens, ")
        .append(count(spend.speechPromptTokens()))
        .append(" text tokens");
    translationTokens(text, spend);
    // Both models a session can touch are priced, so a translated session is not under-reported by
    // the hop it paid for.
    double usd =
        SpendPricing.estimateSpeechUsd(spend.audioTokens(), spend.speechPromptTokens())
            + SpendPricing.estimateTranslationUsd(
                spend.translationInputTokens(), spend.translationOutputTokens());
    return text.append(". Estimated ")
        .append(usd(usd))
        .append(" (tokens measured, price from Google's published rate).")
        .toString();
  }

  private static StringBuilder counts(ProviderSpend spend) {
    return new StringBuilder()
        .append(spend.provider())
        .append(" this session: ")
        .append(count(spend.voicedLines()))
        .append(" lines voiced, ")
        .append(count(spend.prefetchedLines()))
        .append(" prefetched, ");
  }

  /** The translation bucket sized in characters, for a provider that reports no tokens. */
  private static void translation(StringBuilder text, ProviderSpend spend) {
    if (spend.translationCalls() > 0) {
      text.append(", plus ")
          .append(count(spend.translationCalls()))
          .append(" translation calls (")
          .append(count(spend.translationCharacters()))
          .append(" characters)");
    }
  }

  /** The translation bucket sized in the tokens the provider metered for the hop. */
  private static void translationTokens(StringBuilder text, ProviderSpend spend) {
    if (spend.translationCalls() > 0) {
      text.append(", plus ")
          .append(count(spend.translationCalls()))
          .append(" translation calls (")
          .append(count(spend.translationInputTokens() + spend.translationOutputTokens()))
          .append(" tokens)");
    }
  }

  private static String count(long value) {
    return String.format(Locale.US, "%,d", value);
  }

  private static String usd(double value) {
    if (value > 0 && value < SMALLEST_SHOWN_USD) {
      return "under $" + String.format(Locale.US, "%.4f", SMALLEST_SHOWN_USD);
    }
    return String.format(Locale.US, "$%.4f", value);
  }
}
