/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.generated.BuildInfo;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.LineBuffer;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.net.URL;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.function.Supplier;
import javax.swing.ImageIcon;
import javax.swing.JComponent;

class AboutCredits extends JComponent {
  private static final long serialVersionUID = 1L;
  /** Speed of how quickly the scrolling occurs. */
  private static final int MILLIS_PER_RASTER = 20;

  /**
   * Path to Hendrix College's logo - if you want your own logo included, please add it separately
   * rather than replacing this.
   */
  private static final String HENDRIX_LOGO_PATH = "resources/logisim/hendrix.png";

  private final Lines lines;
  private final int preferredWidth;
  private final int preferredHeight;
  private long elapsedMillis;
  private int layoutWidth = -1;
  private int layoutHeight = -1;
  private double layoutScale;
  private Font layoutFont;
  private FontRenderContext layoutContext;

  public AboutCredits(int width, int height) {
    preferredWidth = width;
    preferredHeight = height;
    setOpaque(true);
    final var jvm =
        LineBuffer.format(
            "{{1}} v{{2}} ({{3}})",
            System.getProperty("java.vm.name"),
            System.getProperty("java.version"),
            System.getProperty("java.vendor"));
    System.out.println(S.get("appVersionJvm", jvm));

    lines = new Lines();
    lines
        .title(BuildInfo.displayName)
        .h2(S.get("creditsRevolutionCopyright"))
        .tiny(S.get("creditsUpstreamCopyright", BuildInfo.year))
        .url(BuildInfo.url)
        .space()
        .h1(S.get("creditsDevelopedBy"))
        .text("Moshe Berman")
        .text("Theldo Cruz Franqueira")
        .text("Zhao Hanyuan")
        .text("David H. Hutchens")
        .text("Theo Kluter")
        .text("Torsten Maehne")
        .text("Tom Niget")
        .text("Marcin Orłowski")
        .text("Kevin Walsh")
        .text("Liu Yuchen")
        .tiny(S.get("creditsDevelopedByAndOthers"))
        .space()
        .h1(S.get("creditsRoleFork"))
        .text("Berner Fachhochschule | Haute école spécialisée bernoise")
        .url("https://www.bfh.ch/")
        .text("College of the Holy Cross")
        .url("https://www.holycross.edu")
        .text("Haute École d'Ingénierie et de Gestion du Canton de Vaud")
        .url("https://www.heig-vd.ch/")
        .text("Haute école du paysage, d'ingénierie")
        .text("et d'architecture de Genève")
        .url("https://hepia.hesge.ch")
        .space()
        .h1(S.get("creditsRoleOriginal"))
        .text("Carl Burch")
        .text("Hendrix College")
        .url("http://www.cburch.com/logisim/")
        .img(getClass().getClassLoader().getResource(HENDRIX_LOGO_PATH))
        .space()
        .space()
        .h1(S.get("creditsBuildInfo"))
        .text(S.get("creditsCompiled", BuildInfo.dateIso8601))
        .text(BuildInfo.buildId)
        .space()
        .text(BuildInfo.jvm_version)
        .text(BuildInfo.jvm_vendor);
    Theme.addListener(this, () -> {
      layoutWidth = -1;
      repaint();
    });
  }

  void advance(int millis) {
    elapsedMillis += millis;
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(UiScale.scaled(preferredWidth), UiScale.scaled(preferredHeight));
  }

  static Color surfaceColor() {
    return new Color(Tokens.color(
        "Logisim.sidePanel.background", Tokens.color("Panel.background", Color.WHITE)).getRGB());
  }

  /** Choose opaque text with at least 4.5:1 contrast against the surface actually painted. */
  static Color readableColor(Color requested, Color background) {
    final var opaque = new Color(requested.getRGB());
    if (contrast(opaque, background) >= 4.5) return opaque;
    final var foreground = Tokens.color("Label.foreground", Color.BLACK);
    if (contrast(foreground, background) >= 4.5) return new Color(foreground.getRGB());
    return contrast(Color.BLACK, background) >= contrast(Color.WHITE, background)
        ? Color.BLACK : Color.WHITE;
  }

  static double contrast(Color first, Color second) {
    final var firstLight = luminance(first);
    final var secondLight = luminance(second);
    return (Math.max(firstLight, secondLight) + 0.05)
        / (Math.min(firstLight, secondLight) + 0.05);
  }

  private static double luminance(Color color) {
    return 0.2126 * linear(color.getRed())
        + 0.7152 * linear(color.getGreen())
        + 0.0722 * linear(color.getBlue());
  }

  private static double linear(int channel) {
    final var value = channel / 255.0;
    return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  }

  int getScrollHeight() {
    return lines.totalScrollLinesHeight;
  }

  @Override
  protected void paintComponent(Graphics g) {
    final var graphics = (Graphics2D) g.create();
    try {
      final var background = surfaceColor();
      graphics.setColor(background);
      graphics.fillRect(0, 0, getWidth(), getHeight());
      if (getWidth() <= 0 || getHeight() <= 0) return;
      if (AppPreferences.AntiAliassing.getBoolean()) {
        graphics.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      }

      final var font = UiFonts.body();
      final var context = graphics.getFontRenderContext();
      if (layoutWidth != getWidth() || layoutHeight != getHeight()
          || layoutScale != UiScale.factor() || !font.equals(layoutFont)
          || !context.equals(layoutContext)) {
        lines.initialize(graphics, getWidth());
        layoutWidth = getWidth();
        layoutHeight = getHeight();
        layoutScale = UiScale.factor();
        layoutFont = font;
        layoutContext = context;
      }

      final var maxOffsetY = (long) lines.totalScrollLinesHeight + getHeight();
      final var raster = (long) (elapsedMillis / (double) MILLIS_PER_RASTER * UiScale.factor());
      final var offsetY = (int) (raster % maxOffsetY) - getHeight();
      for (final var line : lines) {
        final var y = line.startY - offsetY;
        if (!isVisible(y, line.displayHeight, getHeight())) continue;
        line.paint(graphics, getWidth(), y, background);
      }
    } finally {
      graphics.dispose();
    }
  }

  static boolean isVisible(int top, int lineHeight, int viewportHeight) {
    return top < viewportHeight && (long) top + lineHeight > 0;
  }

  private class Lines extends ArrayList<CreditsLine> {
    private static final long serialVersionUID = 1L;

    private int totalScrollLinesHeight = 0;

    public void initialize(Graphics2D g, int displayWidth) {
      totalScrollLinesHeight = 0;
      for (final var line : lines) {
        line.startY = totalScrollLinesHeight;
        line.init(g, displayWidth);
        totalScrollLinesHeight += line.displayHeight;
      }
    }

    public Lines space() {
      add(new SpaceLine());
      return this;
    }

    public Lines title(String text) {
      add(new TextLine(
          () -> UiFonts.heading().deriveFont(UiFonts.heading().getSize2D() * 1.5f),
          Tokens::accent, text));
      return this;
    }

    public Lines h1(String text) {
      add(new TextLine(UiFonts::heading, Tokens::accent, text));
      return this;
    }

    public Lines h2(String text) {
      add(new TextLine(UiFonts::bodyBold, AboutCredits::foregroundColor, text));
      return this;
    }

    public Lines url(String text) {
      add(new TextLine(UiFonts::body, Tokens::accent, text));
      return this;
    }

    public Lines text(String text) {
      add(new TextLine(UiFonts::body, AboutCredits::foregroundColor, text));
      return this;
    }

    public Lines tiny(String text) {
      add(new TextLine(UiFonts::small, Tokens::mutedForeground, text));
      return this;
    }

    public Lines img(URL url) {
      add(new ImgLine(url));
      return this;
    }
  }

  private static Color foregroundColor() {
    return Tokens.color("Label.foreground", Color.BLACK);
  }

  private static class TextLine extends CreditsLine {
    private final Supplier<Font> font;
    private final Supplier<Color> color;
    private final String text;
    private final ArrayList<TextLayout> layouts = new ArrayList<>();

    private TextLine(Supplier<Font> font, Supplier<Color> color, String text) {
      this.font = font;
      this.color = color;
      this.text = text;
    }
    @Override
    void init(Graphics2D g, int displayWidth) {
      layouts.clear();
      displayHeight = 0;
      if (text.isEmpty()) return;
      final var attributed = new AttributedString(text);
      attributed.addAttribute(TextAttribute.FONT, font.get());
      final var iterator = attributed.getIterator();
      final var measurer = new LineBreakMeasurer(iterator, g.getFontRenderContext());
      final var available = Math.max(1, displayWidth - 2 * UiScale.scaled(8));
      while (measurer.getPosition() < iterator.getEndIndex()) {
        final var layout = measurer.nextLayout(available);
        layouts.add(layout);
        displayHeight += (int) Math.ceil(layout.getAscent() + layout.getDescent() + layout.getLeading());
      }
    }

    @Override
    void paint(Graphics2D g, int width, int y, Color background) {
      g.setColor(readableColor(color.get(), background));
      var top = y;
      for (final var layout : layouts) {
        layout.draw(g, (width - layout.getAdvance()) / 2, top + layout.getAscent());
        top += (int) Math.ceil(layout.getAscent() + layout.getDescent() + layout.getLeading());
      }
    }
  }

  private static class SpaceLine extends CreditsLine {
    @Override
    void init(Graphics2D g, int width) {
      displayHeight = UiScale.scaled(20);
    }

    @Override
    void paint(Graphics2D g, int width, int y, Color background) {}
  }

  private class ImgLine extends CreditsLine {
    private final ImageIcon img;
    private int imageWidth;
    private int imageHeight;

    private ImgLine(URL url) {
      img = new ImageIcon(url);
    }

    @Override
    void init(Graphics2D g, int width) {
      imageWidth = Math.min(width, UiScale.scaled(img.getIconWidth()));
      imageHeight = Math.max(1, (int) Math.round(
          imageWidth * (double) img.getIconHeight() / img.getIconWidth()));
      displayHeight = imageHeight + UiScale.scaled(20);
    }

    @Override
    void paint(Graphics2D g, int width, int y, Color background) {
      g.drawImage(img.getImage(), (width - imageWidth) / 2, y + UiScale.scaled(10),
          imageWidth, imageHeight, AboutCredits.this);
    }
  }

  private abstract static class CreditsLine {
    protected int displayHeight = 0;
    protected int startY = 0;

    abstract void init(Graphics2D g, int width);

    abstract void paint(Graphics2D g, int width, int y, Color background);
  }
}
