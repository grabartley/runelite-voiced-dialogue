package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.grahambartley.runelite.voiced.dialogue.synthesis.SpendTracker.ProviderSpend;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The {@code ::voicedspend} chat command: what it is called, and how a {@link SpendTracker}
 * snapshot reads back to the player.
 *
 * <p>One chat line per provider used this session, carrying the lines voiced, the lines prefetched,
 * the characters actually sent, the translation hop when there was one, and an estimated cost from
 * {@link SpendPricing}. Every line says the cost is an estimate, because it is: the provider's own
 * dashboard is what actually bills.
 */
public final class SpendReport {

  /** The chat command, without the {@code ::} prefix RuneLite strips before dispatching it. */
  public static final String COMMAND = "voicedspend";

  private static final String NOTHING_SPENT =
      "Nothing has been voiced this session, so nothing has been spent.";

  /**
   * Below this the four-decimal figure would round to zero, which reads as "free" rather than tiny.
   */
  private static final double SMALLEST_SHOWN_USD = 0.0001;

  private SpendReport() {}

  /** Whether a {@code CommandExecuted} command name is this plugin's spend command. */
  public static boolean matches(String command) {
    return command != null && COMMAND.equalsIgnoreCase(command.trim());
  }

  /** The chat lines for a snapshot: one per provider, or a single "nothing spent" line. */
  public static List<String> lines(List<ProviderSpend> snapshot) {
    if (snapshot == null || snapshot.isEmpty()) {
      return Collections.singletonList(NOTHING_SPENT);
    }
    List<String> lines = new ArrayList<>(snapshot.size());
    for (ProviderSpend spend : snapshot) {
      lines.add(line(spend));
    }
    return lines;
  }

  private static String line(ProviderSpend spend) {
    StringBuilder text = new StringBuilder();
    text.append(spend.provider())
        .append(" this session: ")
        .append(count(spend.voicedLines()))
        .append(" lines voiced, ")
        .append(count(spend.prefetchedLines()))
        .append(" prefetched, ")
        .append(count(spend.speechCharacters()))
        .append(" characters sent");
    if (spend.translationCalls() > 0) {
      text.append(", plus ")
          .append(count(spend.translationCalls()))
          .append(" translation calls (")
          .append(count(spend.translationCharacters()))
          .append(" characters)");
    }
    double usd =
        SpendPricing.estimateUsd(
            spend.provider(), spend.speechCharacters(), spend.translationCharacters());
    return text.append(". Estimated spend ")
        .append(usd(usd))
        .append(", an estimate only.")
        .toString();
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
