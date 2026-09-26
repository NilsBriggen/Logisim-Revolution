/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A live preview of what a print or an image export will produce, shown beside the dialog's
 * options.
 *
 * <p>The owner supplies a {@link Renderer} that draws one page through the same painting code as
 * the real output. Rendering a large circuit, or one that has to be simulated first, takes time,
 * so it runs on a background thread: option changes are coalesced, a result that a newer change
 * has overtaken is dropped, and finished pages are cached so that stepping back and forth or
 * toggling an option back does not render again.
 *
 * <p>With more than one page the pane shows previous and next buttons and the page position.
 */
public class PreviewPane extends JPanel {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory.getLogger(PreviewPane.class);

  /** How long option changes are left to settle before a page is rendered, in milliseconds. */
  private static final int SETTLE_DELAY_MS = 120;

  /** Rendered pages kept per pane. */
  private static final int CACHE_SIZE = 24;

  /** Design size, in logical pixels, of the area the page is drawn in. */
  private static final int VIEW_WIDTH = 340;

  private static final int VIEW_HEIGHT = 380;

  /** Edge, in logical pixels, of a square of the transparency checkerboard. */
  private static final int CHECKER = 6;

  /** One background thread for every preview: at most one dialog is open at a time. */
  private static final ExecutorService RENDERER =
      Executors.newSingleThreadExecutor(
          task -> {
            final var thread = new Thread(task, "PreviewRenderer");
            thread.setDaemon(true);
            return thread;
          });

  /** A rendered page and the line of text shown under it. */
  public record Result(BufferedImage image, String caption) {}

  /** Draws one page of the preview. Called on a background thread. */
  @FunctionalInterface
  public interface Renderer {
    /**
     * Renders page {@code index} to fit within {@code maxWidth} by {@code maxHeight} pixels.
     */
    Result render(int index, int maxWidth, int maxHeight) throws Exception;
  }

  private record CacheKey(Object settings, int index, int width, int height) {}

  private final View view = new View();
  private final JLabel caption = new JLabel(" ", SwingConstants.CENTER);
  private final JLabel position = new JLabel(" ", SwingConstants.CENTER);
  private final JButton previous = new JButton();
  private final JButton next = new JButton();
  private final JPanel navigation =
      new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredSize() {
          final var size = super.getPreferredSize();
          size.height = Math.max(size.height, previous.getPreferredSize().height);
          return size;
        }
      };
  private final Timer settle;
  private final IntFunction<String> positionText;
  private final Map<CacheKey, Result> cache =
      new LinkedHashMap<>(16, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Result> eldest) {
          return size() > CACHE_SIZE;
        }
      };

  private Object settings;
  private Renderer renderer;
  private int pageCount;
  private int page;
  private int generation;
  private boolean busy;
  private boolean failed;
  private BufferedImage image;

  /**
   * Creates an empty preview.
   *
   * @param positionText the text naming the page shown, such as "Page 2 of 3", given its
   *     zero-based index
   */
  public PreviewPane(IntFunction<String> positionText) {
    super(new BorderLayout(0, Spacing.sm()));
    this.positionText = positionText;
    setOpaque(false);
    settle = new Timer(SETTLE_DELAY_MS, e -> render());
    settle.setRepeats(false);

    caption.setFont(UiFonts.caption());
    caption.setForeground(Tokens.mutedForeground());
    position.setFont(UiFonts.caption());
    configureStepButton(previous, AppIcons.Id.CHEVRON_LEFT, S.get("previewPreviousPage"));
    configureStepButton(next, AppIcons.Id.CHEVRON_RIGHT, S.get("previewNextPage"));
    previous.addActionListener(e -> showPage(page - 1));
    next.addActionListener(e -> showPage(page + 1));
    navigation.setOpaque(false);
    navigation.add(previous);
    navigation.add(position);
    navigation.add(next);
    position.setBorder(Spacing.border(0, Spacing.SM, 0, Spacing.SM));

    final var footer = new JPanel(new BorderLayout(0, Spacing.xs()));
    footer.setOpaque(false);
    footer.add(caption, BorderLayout.NORTH);
    footer.add(navigation, BorderLayout.SOUTH);

    add(view, BorderLayout.CENTER);
    add(footer, BorderLayout.SOUTH);
    view.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            schedule();
          }
        });
    view.getAccessibleContext().setAccessibleName(S.get("previewTitle"));
    updateNavigation();
  }

  private static void configureStepButton(JButton button, AppIcons.Id icon, String name) {
    button.setIcon(AppIcons.get(icon, 14));
    button.putClientProperty("JButton.buttonType", "toolBarButton");
    button.setMargin(new java.awt.Insets(Spacing.XS / 2, Spacing.XS, Spacing.XS / 2, Spacing.XS));
    button.setToolTipText(name);
    button.getAccessibleContext().setAccessibleName(name);
  }

  /**
   * Shows new content. Call on the event thread whenever an option that changes the output does.
   *
   * @param settings everything the rendering depends on, compared by {@code equals} to reuse
   *     pages already rendered
   * @param pageCount how many pages there are; zero shows an empty preview
   * @param renderer draws one page
   */
  public void setContent(Object settings, int pageCount, Renderer renderer) {
    this.settings = settings;
    this.renderer = renderer;
    this.pageCount = pageCount;
    if (page >= pageCount) page = Math.max(0, pageCount - 1);
    updateNavigation();
    schedule();
  }

  /** Whether a page is waiting to be rendered or is being rendered. */
  public boolean isBusy() {
    return busy || settle.isRunning();
  }

  /** The zero-based page shown. */
  public int getPage() {
    return page;
  }

  /** The line under the page, such as the pixel size of an exported image. */
  public String getCaption() {
    return caption.getText();
  }

  /** Shows page {@code index}, when it exists. */
  public void showPage(int index) {
    if (index < 0 || index >= pageCount || index == page) return;
    page = index;
    updateNavigation();
    render();
  }

  private void updateNavigation() {
    // The row keeps its height with a single page, so that selecting a second one does not
    // shrink the page drawn above it.
    previous.setVisible(pageCount > 1);
    next.setVisible(pageCount > 1);
    position.setVisible(pageCount > 1);
    previous.setEnabled(page > 0);
    next.setEnabled(page + 1 < pageCount);
    position.setText(pageCount > 0 ? positionText.apply(page) : " ");
  }

  private void schedule() {
    busy = true;
    settle.restart();
  }

  private void render() {
    settle.stop();
    final var ticket = ++generation;
    if (renderer == null || pageCount == 0) {
      image = null;
      failed = false;
      busy = false;
      caption.setText(" ");
      view.repaint();
      return;
    }
    final var inset = Spacing.md();
    final var width = Math.max(1, view.getWidth() - 2 * inset);
    final var height = Math.max(1, view.getHeight() - 2 * inset);
    if (view.getWidth() <= 0 || view.getHeight() <= 0) {
      busy = false;
      return;
    }
    final var key = new CacheKey(settings, page, width, height);
    final var cached = cache.get(key);
    if (cached != null) {
      show(cached);
      return;
    }
    busy = true;
    final var job = renderer;
    final var index = page;
    RENDERER.execute(
        () -> {
          Result result;
          try {
            result = job.render(index, width, height);
          } catch (Exception | OutOfMemoryError e) {
            logger.warn("Could not render the preview", e);
            result = null;
          }
          final var done = result;
          SwingUtilities.invokeLater(
              () -> {
                if (ticket != generation) return;
                if (done == null) {
                  image = null;
                  failed = true;
                  busy = false;
                  caption.setText(S.get("previewUnavailable"));
                  view.repaint();
                  return;
                }
                cache.put(key, done);
                show(done);
              });
        });
  }

  private void show(Result result) {
    image = result.image();
    failed = false;
    busy = false;
    caption.setText(Objects.requireNonNullElse(result.caption(), " "));
    view.getAccessibleContext().setAccessibleDescription(result.caption());
    view.repaint();
  }

  @Override
  public void removeNotify() {
    // A closed dialog must not keep a render queued or accept one that finishes later.
    settle.stop();
    generation++;
    busy = false;
    super.removeNotify();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    // The step buttons' icons and the caption colour follow the theme.
    if (caption != null) {
      caption.setForeground(Tokens.mutedForeground());
      previous.setIcon(AppIcons.get(AppIcons.Id.CHEVRON_LEFT, 14));
      next.setIcon(AppIcons.get(AppIcons.Id.CHEVRON_RIGHT, 14));
    }
  }

  /** The recessed well the page is drawn in. A panel, so that it has an accessible context. */
  private final class View extends JPanel {
    private static final long serialVersionUID = 1L;

    View() {
      super(null);
      setOpaque(true);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(UiScale.scaled(VIEW_WIDTH), UiScale.scaled(VIEW_HEIGHT));
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(UiScale.scaled(VIEW_WIDTH / 2), UiScale.scaled(VIEW_HEIGHT / 2));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      final var g = (Graphics2D) graphics.create();
      try {
        g.setColor(Tokens.previewBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Tokens.divider());
        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        final var shown = image;
        if (shown == null) {
          paintMessage(g, failed ? S.get("previewUnavailable") : S.get("previewRendering"));
          return;
        }
        final var x = (getWidth() - shown.getWidth()) / 2;
        final var y = (getHeight() - shown.getHeight()) / 2;
        final var shadow = Math.max(1, UiScale.scaled(3));
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(x + shadow, y + shadow, shown.getWidth(), shown.getHeight());
        if (shown.getColorModel().hasAlpha()) {
          paintChecker(g, x, y, shown.getWidth(), shown.getHeight());
        }
        g.drawImage(shown, x, y, null);
        g.setColor(Tokens.divider());
        g.drawRect(x - 1, y - 1, shown.getWidth() + 1, shown.getHeight() + 1);
      } finally {
        g.dispose();
      }
    }

    private void paintMessage(Graphics2D g, String text) {
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setFont(UiFonts.caption());
      g.setColor(Tokens.mutedForeground());
      final var metrics = g.getFontMetrics();
      g.drawString(
          text,
          (getWidth() - metrics.stringWidth(text)) / 2,
          (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
    }

    /** Marks where an exported image is transparent, as image editors do. */
    private void paintChecker(Graphics2D g, int x, int y, int width, int height) {
      final var clip = g.getClip();
      g.clipRect(x, y, width, height);
      final var cell = Math.max(2, UiScale.scaled(CHECKER));
      g.setColor(Color.WHITE);
      g.fillRect(x, y, width, height);
      g.setColor(new Color(0xE6E6E6));
      for (var row = 0; row * cell < height; row++) {
        for (var col = row % 2; col * cell < width; col += 2) {
          g.fillRect(x + col * cell, y + row * cell, cell, cell);
        }
      }
      g.setClip(clip);
    }
  }
}
