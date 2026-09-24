/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.theme;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;

/**
 * The application's interface icons, drawn from SVG so they stay sharp at any scale and take their
 * colour from the theme.
 *
 * <p>These are the icons of the interface itself: menus, toolbars, panels, the status bar. The
 * icons that stand for circuit components use semantic vectors in {@code gui.icons.ComponentIcons}
 * or their own attribute-dependent painters.
 *
 * <p>Glyphs use the Lucide icon set (ISC licence) and application-specific line drawings;
 * see {@code LICENSE-lucide.txt} and the individual SVG notices beside the files.
 */
public final class AppIcons {

  /** Where the SVG files live on the classpath. */
  private static final String PATH = "resources/logisim/svg/";

  /** Nominal icon size before the interface scale is applied. */
  public static final int SIZE = 16;

  /** An icon of the interface. The name is the SVG file's base name. */
  public enum Id {
    NEW("new"),
    OPEN("open"),
    SAVE("save"),
    SAVE_AS("save-as"),
    PRINT("print"),
    EXPORT("export"),
    UNDO("undo"),
    REDO("redo"),
    CUT("cut"),
    COPY("copy"),
    PASTE("paste"),
    DELETE("delete"),
    ADD("add"),
    EDIT("edit"),
    SEARCH("search"),
    FILTER("filter"),
    CLEAR("clear"),
    CLOSE("close"),
    CHECK("check"),
    RUN("run"),
    PAUSE("pause"),
    STEP("step"),
    TICK_HALF("tick-half"),
    TICK_FULL("tick-full"),
    TICK_ENABLED("tick-enabled"),
    RESET("reset"),
    ZOOM_IN("zoom-in"),
    ZOOM_OUT("zoom-out"),
    ZOOM_FIT("zoom-fit"),
    GRID("grid"),
    EXPLORER("explorer"),
    LIBRARY("library"),
    SIMULATION("simulation"),
    SETTINGS("settings"),
    INSPECTOR("inspector"),
    TERMINAL("terminal"),
    CHEVRON_DOWN("chevron-down"),
    CHEVRON_RIGHT("chevron-right"),
    CHEVRON_LEFT("chevron-left"),
    CHEVRON_UP("chevron-up"),
    ERROR("error"),
    WARNING("warning"),
    INFO("info"),
    QUESTION("question"),
    BREAKPOINT("breakpoint"),
    LOG("log"),
    WAVEFORM("waveform"),
    TEST("test"),
    CHIP("chip"),
    HEX("hex"),
    ANALYZE("analyze"),
    CIRCUIT("circuit"),
    HDL("hdl"),
    FOLDER("folder"),
    MOVE_UP("move-up"),
    MOVE_DOWN("move-down"),
    APPEARANCE("appearance"),
    PIN("pin"),
    STAR("star"),
    HISTORY("history"),
    LIST_FILTER("list-filter"),
    MORE("more"),
    POINTER("pointer"),
    HAND("hand"),
    TYPE("type"),
    IMAGE("image"),
    COMPILE("compile"),
    HOME("home"),
    ARROW_RIGHT("arrow-right"),
    ARROW_LEFT("arrow-left"),
    ARROW_UP("arrow-up"),
    ARROW_DOWN("arrow-down"),
    EYE("eye"),
    DRAW_LINE("draw-line"),
    DRAW_CURVE("draw-curve"),
    DRAW_POLYLINE("draw-polyline"),
    DRAW_POLYGON("draw-polygon"),
    DRAW_RECTANGLE("draw-rectangle"),
    DRAW_ROUNDED_RECTANGLE("draw-rounded-rectangle"),
    DRAW_ELLIPSE("draw-ellipse"),
    WIRE("wire");

    private final String fileName;

    Id(String fileName) {
      this.fileName = fileName;
    }

    /** The classpath resource holding this glyph. */
    public String resource() {
      return PATH + fileName + ".svg";
    }
  }

  private static final Map<String, FlatSVGIcon> cache = new ConcurrentHashMap<>();

  static {
    // A theme change alters every icon's tint, and FlatSVGIcon caches what it has drawn.
    Theme.addListener(AppIcons::clearCache);
  }

  private AppIcons() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /** The icon for {@code id} at the standard size, tinted to suit the current surface. */
  public static FlatSVGIcon get(Id id) {
    return get(id, SIZE);
  }

  /**
   * The icon for {@code id}, {@code size} pixels square before scaling.
   *
   * @param size unscaled edge length; the interface scale is applied here so callers pass design
   *     sizes rather than device pixels
   */
  public static FlatSVGIcon get(Id id, int size) {
    return icon(id, size, null);
  }

  /** The icon for {@code id} painted in {@code color} rather than the default foreground. */
  public static FlatSVGIcon colored(Id id, int size, Color color) {
    return icon(id, size, color);
  }

  /** The icon for {@code id} in the accent colour, for an active or selected control. */
  public static FlatSVGIcon accented(Id id, int size) {
    return variant(id, size, "accent", Tokens::accent);
  }

  /** A greyed-out variant, for a control that exists but cannot be used. */
  public static Icon disabled(Id id, int size) {
    return variant(id, size, "disabled", Tokens::iconDisabled);
  }

  private static FlatSVGIcon icon(Id id, int size, Color color) {
    return variant(id, size, color == null ? "auto" : "color:" + color.getRGB(),
        color == null ? Tokens::iconForeground : () -> color);
  }

  private static FlatSVGIcon variant(
      Id id, int size, String variant, java.util.function.Supplier<Color> tint) {
    final var key = id.name() + '#' + size + '#' + variant;
    return cache.computeIfAbsent(
        key,
        ignored -> {
          // FlatSVGIcon applies UIScale itself, including to instances already held by components.
          final var icon = new FlatSVGIcon(id.resource(), size, size);
          icon.setColorFilter(filter(tint));
          return icon;
        });
  }

  /**
   * Repaints every colour in the glyph as one flat tint.
   *
   * <p>The glyphs are single-colour line drawings, so there is nothing to preserve, and asking the
   * supplier on every paint means a theme change is picked up without rebuilding the icon.
   */
  private static FlatSVGIcon.ColorFilter filter(java.util.function.Supplier<Color> tint) {
    return new FlatSVGIcon.ColorFilter(ignored -> tint.get());
  }

  /** Drops the cached icons, so the next paint rebuilds them for the new theme or scale. */
  public static void clearCache() {
    cache.clear();
  }
}
