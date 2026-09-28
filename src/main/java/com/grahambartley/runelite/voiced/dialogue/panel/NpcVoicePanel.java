package com.grahambartley.runelite.voiced.dialogue.panel;

import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceCatalog;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceOverrideStore;
import com.grahambartley.runelite.voiced.dialogue.profile.RecentNpcSpeakers;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import okhttp3.OkHttpClient;

public final class NpcVoicePanel extends PluginPanel {

  static final String LIST_CARD = "list";
  static final String DETAIL_CARD = "detail";

  private final NpcVoiceCatalog catalog;
  private final NpcNameResolver nameResolver;
  private final Consumer<Runnable> uiThread;
  private final BooleanSupplier showing;
  private final CardLayout cards = new CardLayout();
  private final JPanel content = new JPanel(cards);
  private final NpcListView listView;
  private final NpcDetailView detailView;
  private final Set<Integer> requestedNames = new HashSet<>();
  private String shownCard = LIST_CARD;

  public NpcVoicePanel(
      NpcVoiceCatalog catalog,
      RecentNpcSpeakers recentSpeakers,
      NpcVoiceOverrideStore store,
      NpcNameResolver nameResolver,
      OkHttpClient httpClient,
      ExecutorService chatheadExecutor) {
    this(
        catalog,
        recentSpeakers,
        store,
        nameResolver,
        new ChatheadImages(
            httpClient,
            ChatheadImages.WIKI_FILE_PATH,
            chatheadExecutor,
            SwingUtilities::invokeLater),
        SwingUtilities::invokeLater,
        null);
  }

  NpcVoicePanel(
      NpcVoiceCatalog catalog,
      RecentNpcSpeakers recentSpeakers,
      NpcVoiceOverrideStore store,
      NpcNameResolver nameResolver,
      ChatheadImages chatheads,
      Consumer<Runnable> uiThread,
      BooleanSupplier showing) {
    super(false);
    this.catalog = catalog;
    this.nameResolver = nameResolver;
    this.uiThread = uiThread;
    this.showing = showing == null ? this::isShowing : showing;
    setLayout(new BorderLayout(0, 10));
    setBorder(new EmptyBorder(10, 10, 10, 10));
    setBackground(ColorScheme.DARK_GRAY_COLOR);

    listView =
        new NpcListView(
            new NpcListEntries(catalog),
            recentSpeakers::newestFirst,
            store::overriddenIds,
            chatheads,
            this::openDetail,
            this::resolveNames);
    detailView = new NpcDetailView(catalog, store, chatheads, this::showList, this::refresh);

    add(title(), BorderLayout.NORTH);
    content.setBackground(ColorScheme.DARK_GRAY_COLOR);
    content.add(listView, LIST_CARD);
    content.add(detailView, DETAIL_CARD);
    add(content, BorderLayout.CENTER);
    recentSpeakers.setListener(this::refreshListLater);
  }

  public static BufferedImage navigationIcon() {
    return PanelIcons.navigation();
  }

  @Override
  public void onActivate() {
    refresh();
    if (LIST_CARD.equals(shownCard)) {
      listView.focusSearch();
    }
  }

  public void refreshLater() {
    whenShowing(
        () -> {
          if (LIST_CARD.equals(shownCard)) {
            listView.refresh();
          } else {
            detailView.reload();
          }
        });
  }

  private void refreshListLater() {
    whenShowing(this::refresh);
  }

  private void whenShowing(Runnable redraw) {
    uiThread.accept(
        () -> {
          if (showing.getAsBoolean()) {
            redraw.run();
          }
        });
  }

  void refresh() {
    if (LIST_CARD.equals(shownCard)) {
      listView.refresh();
    }
  }

  private void openDetail(NpcListEntry entry) {
    detailView.open(entry);
    show(DETAIL_CARD);
  }

  private void showList() {
    show(LIST_CARD);
    listView.refresh();
  }

  private void show(String card) {
    shownCard = card;
    cards.show(content, card);
  }

  private void resolveNames(Set<Integer> ids) {
    Set<Integer> fresh = new HashSet<>(ids);
    fresh.removeAll(requestedNames);
    if (fresh.isEmpty()) {
      return;
    }
    requestedNames.addAll(fresh);
    nameResolver.resolve(fresh, names -> uiThread.accept(() -> remember(names)));
  }

  private void remember(Map<Integer, String> names) {
    names.forEach(catalog::remember);
    refresh();
  }

  private static JPanel title() {
    JPanel title = new JPanel(new GridLayout(2, 1));
    title.setOpaque(false);
    JLabel heading = new JLabel("NPC Voices");
    heading.setFont(FontManager.getRunescapeBoldFont());
    heading.setForeground(ColorScheme.BRAND_ORANGE);
    JLabel blurb = new JLabel("Choose how any NPC sounds to you.");
    blurb.setFont(FontManager.getRunescapeSmallFont());
    blurb.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    title.add(heading);
    title.add(blurb);
    return title;
  }

  String shownCard() {
    return shownCard;
  }

  NpcListView listView() {
    return listView;
  }

  NpcDetailView detailView() {
    return detailView;
  }
}
