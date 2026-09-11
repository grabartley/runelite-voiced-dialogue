package com.grahambartley.runelite.voiced.dialogue.speech.spend;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider;
import com.grahambartley.runelite.voiced.dialogue.speech.spend.SpendTracker.ProviderSpend;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SpendReport {

  public static final String COMMAND = "voicedspend";

  private static final String NOTHING_SPENT =
      "Nothing has been voiced this session, so nothing has been spent.";

  private static final double SMALLEST_SHOWN_USD = 0.0001;

  private SpendReport() {}

  public static boolean matches(String command) {
    return command != null && COMMAND.equalsIgnoreCase(command.trim());
  }

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

  private static void translation(StringBuilder text, ProviderSpend spend) {
    if (spend.translationCalls() > 0) {
      text.append(", plus ")
          .append(count(spend.translationCalls()))
          .append(" translation calls (")
          .append(count(spend.translationCharacters()))
          .append(" characters)");
    }
  }

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
