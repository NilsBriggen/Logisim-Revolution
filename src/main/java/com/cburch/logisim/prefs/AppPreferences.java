/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.AppIdentity;
import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.gui.menu.Menu;
import com.cburch.logisim.gui.menu.MenuItemImpl;
import com.cburch.logisim.gui.start.Startup;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.DesktopScale;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.PropertyChangeWeakSupport;
import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public class AppPreferences {
  // Export and print jobs may run off the EDT, so keep their palette override thread-local.
  private static final ThreadLocal<Boolean> PRINT_VIEW_COLORS = new ThreadLocal<>();

  private static final class PrintViewColorPreference extends PrefMonitorInt {
    private final int printValue;

    private PrintViewColorPreference(String name, int printValue) {
      super(name, printValue);
      this.printValue = printValue;
    }

    @Override
    public Integer get() {
      return inPrintView() ? printValue : super.get();
    }
  }

  //
  // LocalePreference
  //
  private static class LocalePreference extends PrefMonitorString {
    /**
     * Finds the Locale corresponding to the given language string.
     *
     * @param lang Language string to search for.
     * @return The corresponding Locale or null if not found.
     */
    private static Locale findLocale(String lang) {
      Locale[] check;
      for (int set = 0; set < 2; set++) {
        check = (set == 0)
            ? new Locale[] {Locale.getDefault(), Locale.ENGLISH}
            : Locale.getAvailableLocales();
        for (Locale loc : check) {
          if (loc != null && loc.getLanguage().equals(lang)) {
            return loc;
          }
        }
      }
      return null;
    }

    public LocalePreference() {
      super("locale", "");

      final var localeStr = this.get();
      if (!("".equals(localeStr))) {
        LocaleManager.setLocale(Locale.forLanguageTag(localeStr));
      }
      LocaleManager.addLocaleListener(myListener);
      myListener.localeChanged();
    }

    @Override
    public void set(String value) {
      if (findLocale(value) != null) {
        super.set(value);
      }
    }
  }

  //
  // methods for accessing preferences
  //
  private static class MyListener implements PreferenceChangeListener, LocaleListener {
    @Override
    public void localeChanged() {
      final var loc = LocaleManager.getLocale();
      final var lang = loc.getLanguage();
      if (LOCALE != null) {
        LOCALE.set(lang);
      }
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent event) {
      final var prefs = event.getNode();
      final var prop = event.getKey();
      if (prop.equals(TEMPLATE_TYPE)) {
        int oldValue = templateType;
        int value = prefs.getInt(TEMPLATE_TYPE, TEMPLATE_UNKNOWN);
        if (value != oldValue) {
          templateType = value;
          propertySupport.firePropertyChange(TEMPLATE, oldValue, value);
          propertySupport.firePropertyChange(TEMPLATE_TYPE, oldValue, value);
        }
      } else if (prop.equals(TEMPLATE_FILE)) {
        final var oldValue = templateFile;
        final var value = convertFile(prefs.get(TEMPLATE_FILE, null));
        if (!Objects.equals(value, oldValue)) {
          templateFile = value;
          if (templateType == TEMPLATE_CUSTOM) {
            customTemplate = null;
            propertySupport.firePropertyChange(TEMPLATE, oldValue, value);
          }
          propertySupport.firePropertyChange(TEMPLATE_FILE, oldValue, value);
        }
      }
    }
  }

  //
  // PropertyChangeSource methods
  //
  public static void addPropertyChangeListener(PropertyChangeListener listener) {
    propertySupport.addPropertyChangeListener(listener);
  }

  public static void addPropertyChangeListener(
      String propertyName, PropertyChangeListener listener) {
    propertySupport.addPropertyChangeListener(propertyName, listener);
  }

  public static void clear() {
    try {
      getPrefs(true).clear();
    } catch (BackingStoreException ignored) {
    }
  }

  private static File convertFile(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      return null;
    } else {
      final var file = new File(fileName);
      return file.canRead() ? file : null;
    }
  }

  private static <E> PrefMonitor<E> create(PrefMonitor<E> monitor) {
    return monitor;
  }

  /** Whether this thread is drawing for paper or for an exported image rather than for a screen. */
  public static boolean inPrintView() {
    return Boolean.TRUE.equals(PRINT_VIEW_COLORS.get());
  }

  public static void runWithPrintViewColors(Runnable action) {
    final var previous = PRINT_VIEW_COLORS.get();
    PRINT_VIEW_COLORS.set(true);
    try {
      action.run();
    } finally {
      if (previous == null) {
        PRINT_VIEW_COLORS.remove();
      } else {
        PRINT_VIEW_COLORS.set(previous);
      }
    }
  }

  static void firePropertyChange(String property, boolean oldVal, boolean newVal) {
    propertySupport.firePropertyChange(property, oldVal, newVal);
  }

  static void firePropertyChange(String property, Object oldVal, Object newVal) {
    // A colour the user has just picked belongs to the theme it was picked for, not to both.
    ThemeColorPrefs.mirrorChange(property);
    propertySupport.firePropertyChange(property, oldVal, newVal);
  }

  /**
   * Announces a preference that the store says has changed, rather than one this program set.
   *
   * <p>The distinction matters for the colours kept per theme. A store notification arrives on
   * another thread after the fact, and carries no value of its own: a monitor answering one falls
   * back to the default it was constructed with, which for the drawing colours is the light one.
   * Treating that as a colour the user had chosen would file the light colour under whichever
   * theme happened to be showing, and the choice would then outlive every later change to the
   * shipped palette.
   */
  static void firePropertyChangeFromStore(String property, Object oldVal, Object newVal) {
    propertySupport.firePropertyChange(property, oldVal, newVal);
  }

  private static Template getCustomTemplate() {
    final var toRead = templateFile;
    if (customTemplateFile == null || !(customTemplateFile.equals(toRead))) {
      if (toRead == null) {
        customTemplate = null;
        customTemplateFile = null;
      } else {
        try (final var reader = new FileInputStream(toRead)) {
          customTemplate = Template.create(reader);
          customTemplateFile = templateFile;
        } catch (Exception t) {
          setTemplateFile(null);
          customTemplate = null;
          customTemplateFile = null;
        }
      }
    }
    return customTemplate == null ? getPlainTemplate() : customTemplate;
  }

  public static Template getEmptyTemplate() {
    if (emptyTemplate == null) {
      emptyTemplate = Template.createEmpty();
    }
    return emptyTemplate;
  }

  private static Template getPlainTemplate() {
    if (plainTemplate == null) {
      final var ld = Startup.class.getClassLoader();
      final var in = ld.getResourceAsStream("resources/logisim/default.templ");
      if (in == null) {
        plainTemplate = getEmptyTemplate();
      } else {
        try {
          try (in) {
            plainTemplate = Template.create(in);
          }
        } catch (Exception e) {
          plainTemplate = getEmptyTemplate();
        }
      }
    }
    return plainTemplate;
  }

  public static Preferences getPrefs() {
    return getPrefs(false);
  }

  private static Preferences getPrefs(boolean shouldClear) {
    if (prefs == null) {
      synchronized (AppPreferences.class) {
        if (prefs == null) {
          final var p = Preferences.userRoot().node(AppIdentity.PREFERENCES_PATH);
          if (shouldClear) {
            try {
              p.clear();
            } catch (BackingStoreException ignored) {
            }
          }
          myListener = new MyListener();
          p.addPreferenceChangeListener(myListener);
          prefs = p;

          setTemplateFile(convertFile(p.get(TEMPLATE_FILE, null)));
          setTemplateType(p.getInt(TEMPLATE_TYPE, TEMPLATE_PLAIN));
        }
      }
    }
    return prefs;
  }

  //
  // recent projects
  //
  public static List<File> getRecentFiles() {
    return recentProjects.getRecentFiles();
  }

  //
  // template methods
  //
  public static Template getTemplate() {
    getPrefs();
    return switch (templateType) {
      case TEMPLATE_EMPTY -> getEmptyTemplate();
      case TEMPLATE_CUSTOM -> getCustomTemplate();
      default -> getPlainTemplate();
    };
  }

  public static File getTemplateFile() {
    getPrefs();
    return templateFile;
  }

  //
  // accessor methods
  //
  public static int getTemplateType() {
    getPrefs();
    int ret = templateType;
    if (ret == TEMPLATE_CUSTOM && templateFile == null) {
      ret = TEMPLATE_UNKNOWN;
    }
    return ret;
  }

  public static void handleGraphicsAcceleration() {
    final var accel = GRAPHICS_ACCELERATION.get();
    try {
      System.setProperty("sun.java2d.opengl", Boolean.toString(accel.equals(ACCEL_OPENGL)));
      System.setProperty("sun.java2d.d3d", Boolean.toString(accel.equals(ACCEL_D3D)));
      System.setProperty("sun.java2d.metal", Boolean.toString(accel.equals(ACCEL_METAL)));
    } catch (Exception ignored) {
      System.err.println("Note: Could not enable " + accel + " graphics acceleration.");
    }
  }

  public static void removePropertyChangeListener(PropertyChangeListener listener) {
    propertySupport.removePropertyChangeListener(listener);
  }

  public static void removePropertyChangeListener(
      String propertyName, PropertyChangeListener listener) {
    propertySupport.removePropertyChangeListener(propertyName, listener);
  }

  public static void setTemplateFile(File value) {
    getPrefs();
    setTemplateFile(value, null);
  }

  public static void setTemplateFile(File value, Template template) {
    getPrefs();
    if (value != null && !value.canRead()) {
      value = null;
    }
    if (!Objects.equals(value, templateFile)) {
      try {
        customTemplateFile = template == null ? null : value;
        customTemplate = template;
        getPrefs().put(TEMPLATE_FILE, value == null ? "" : value.getCanonicalPath());
      } catch (IOException ignored) {
      }
    }
  }

  public static void setTemplateType(int value) {
    getPrefs();
    if (value != TEMPLATE_PLAIN && value != TEMPLATE_EMPTY && value != TEMPLATE_CUSTOM) {
      value = TEMPLATE_UNKNOWN;
    }
    if (value != TEMPLATE_UNKNOWN && templateType != value) {
      getPrefs().putInt(TEMPLATE_TYPE, value);
    }
  }

  public static int getDownScaled(int value, float extScale) {
    getPrefs();
    return (int) Math.round(value / (UiScale.factor() * extScale));
  }

  public static int getDownScaled(int value) {
    getPrefs();
    return UiScale.unscaled(value);
  }

  public static double getDownScaled(double value) {
    getPrefs();
    return value / UiScale.factor();
  }

  public static int getScaled(int value, float extScale) {
    getPrefs();
    return (int) Math.round(value * (UiScale.factor() * extScale));
  }

  public static int getScaled(int value) {
    getPrefs();
    return UiScale.scaled(value);
  }

  public static float getScaled(float value) {
    getPrefs();
    return UiScale.scaled(value);
  }

  public static float getScaled(float value, float extscale) {
    getPrefs();
    return UiScale.scaled(value) * extscale;
  }

  public static double getScaled(double value) {
    getPrefs();
    return UiScale.scaled(value);
  }

  public static Font getScaledFont(Font myfont) {
    if (myfont != null) {
      return myfont.deriveFont(getScaled((float) FONT_SIZE));
    } else {
      return null;
    }
  }

  public static Font getScaledFont(Font myfont, float scale) {
    if (myfont != null) {
      return myfont.deriveFont(getScaled((float) FONT_SIZE, scale));
    } else {
      return null;
    }
  }

  public static ImageIcon getScaledImageIcon(ImageIcon icon) {
    final var iconImage = icon.getImage();
    return new ImageIcon(
        iconImage.getScaledInstance(getScaled(IconSize), getScaled(IconSize), Image.SCALE_SMOOTH));
  }

  public static ImageIcon getScaledImageIcon(ImageIcon icon, float scale) {
    final var iconImage = icon.getImage();
    return new ImageIcon(
        iconImage.getScaledInstance(
            getScaled(IconSize, scale), getScaled(IconSize, scale), Image.SCALE_SMOOTH));
  }

  /**
   * Determines the appropriate font style (PLAIN or BOLD) for the given font.
   *
   * This is a heuristic to prevent "faux bold" rendering artifacts. Fonts with explicit
   * weights (e.g. Medium, Light) are typically standalone faces. Applying the BOLD style
   * to them forces synthetic bolding, which degrades rendering quality ("smearing").
   *
   * This method returns Font.PLAIN for such weighted fonts to ensure crisp rendering,
   * while preserving Font.BOLD for standard fonts to maintain typical emphasis.
   *
   * @param fontName The name of the font family.
   * @return Font.PLAIN if the font name suggests a specific weight, otherwise Font.BOLD.
   */
  public static int getPreferredFontStyle(String fontName) {
    String font = fontName.toLowerCase();
    if (font.contains("medium") || font.contains("light")
        || font.contains("thin") || font.contains("regular")
        || font.contains("semibold") || font.contains("extrabold")) {
      return Font.PLAIN;
    }
    return Font.BOLD;
  }


  public static void updateRecentFile(File file) {
    recentProjects.updateRecent(file);
  }

  // class variables for maintaining consistency between properties,
  // internal variables, and other classes
  private static Preferences prefs = null;

  private static MyListener myListener = null;
  private static final PropertyChangeWeakSupport propertySupport =
      new PropertyChangeWeakSupport(AppPreferences.class);

  // Autosave preferences
  public static final String AUTOSAVE_ENABLE = "autosaveEnabled";
  public static final String AUTOSAVE_PERIOD = "autosaveInterval";

  public static final PrefMonitor<Boolean> AUTOSAVE_ENABLED = create(new PrefMonitorBoolean(AUTOSAVE_ENABLE, true));
  public static final PrefMonitor<Integer> AUTOSAVE_INTERVAL = create(new PrefMonitorInt(AUTOSAVE_PERIOD, 30));

  // Template preferences
  public static final int IconSize = 16;
  public static final int FONT_SIZE = 14;
  public static final int ICON_BORDER = 2;
  public static final int BOX_SIZE = IconSize + 2 * ICON_BORDER;
  public static final int TEMPLATE_UNKNOWN = -1;
  public static final int TEMPLATE_EMPTY = 0;
  public static final int TEMPLATE_PLAIN = 1;
  public static final int TEMPLATE_CUSTOM = 2;
  public static final String TEMPLATE = "template";
  public static final String TEMPLATE_TYPE = "templateType";
  public static final String TEMPLATE_FILE = "templateFile";
  private static int templateType = TEMPLATE_PLAIN;

  private static File templateFile = null;

  private static Template plainTemplate = null;
  private static Template emptyTemplate = null;
  private static Template customTemplate = null;
  private static File customTemplateFile = null;

  public static int getIconSize() {
    return getScaled(IconSize);
  }

  public static int getIconBorder() {
    return getScaled(ICON_BORDER);
  }

  // International preferences
  public static final String SHAPE_SHAPED = "shaped"; // ANSI

  public static final String SHAPE_RECTANGULAR = "rectangular"; // IEC
  // public static final String SHAPE_DIN40700 = "din40700";

  private static String getDefaultGateShape() {
    return "ru".equalsIgnoreCase(Locale.getDefault().getLanguage())
        ? SHAPE_RECTANGULAR
        : SHAPE_SHAPED;
  }

  public static final PrefMonitor<String> GATE_SHAPE =
      create(
          new PrefMonitorStringOpts(
              "gateShape", new String[] {SHAPE_SHAPED, SHAPE_RECTANGULAR}, getDefaultGateShape()));
  public static final PrefMonitor<String> LOCALE = create(new LocalePreference());

  // FPGA Commander Preferences
  public static final PrefMonitor<String> FPGA_Workspace =
      create(
          new PrefMonitorString(
              "FPGAWorkspace", System.getProperty("user.home") + File.separator + "logisim_evolution_workspace"));
  public static final PrefMonitor<String> HdlType =
      create(
          new PrefMonitorStringOpts(
              "hdlType",
              new String[] {
                HdlGeneratorFactory.VHDL, HdlGeneratorFactory.VERILOG, HdlGeneratorFactory.NONE
              },
              HdlGeneratorFactory.VHDL));
  public static final PrefMonitor<String> SelectedBoard =
      create(new PrefMonitorString("SelectedBoard", null));

  public static final FpgaBoards Boards = new FpgaBoards();

  public static final PrefMonitor<Boolean> SuppressGatedClockWarnings =
      create(new PrefMonitorBoolean("NoGatedClockWarnings", false));
  public static final PrefMonitor<Boolean> SuppressOpenPinWarnings =
      create(new PrefMonitorBoolean("NoOpenPinWarnings", false));
  public static final PrefMonitor<Boolean> VhdlKeywordsUpperCase =
      create(new PrefMonitorBoolean("VhdlKeywordsUpperCase", true));
  // file preferences
  public static final PrefMonitor<Boolean> REMOVE_UNUSED_LIBRARIES =
      create(new PrefMonitorBoolean("removeUnusedLibs", false));
  // Window preferences
  public static final String TOOLBAR_HIDDEN = "hidden";
  public static final PrefMonitor<Boolean> SHOW_TICK_RATE =
      create(new PrefMonitorBoolean("showTickRate", false));
  public static final PrefMonitor<String> TOOLBAR_PLACEMENT =
      create(
          new PrefMonitorStringOpts(
              "toolbarPlacement",
              new String[] {
                  Direction.NORTH.toString(),
                  Direction.SOUTH.toString(),
                  Direction.EAST.toString(),
                  Direction.WEST.toString(),
                  TOOLBAR_HIDDEN
              },
              Direction.NORTH.toString()));

  /** Unused: nothing has ever read this. Kept so an existing preferences file is not disturbed. */
  @Deprecated
  public static final PrefMonitor<String> CANVAS_PLACEMENT =
      create(
          new PrefMonitorStringOpts(
              "canvasPlacement",
              new String[] {
                  Direction.EAST.toString(),
                  Direction.WEST.toString()},
              Direction.EAST.toString()));

  public static final String THEME_LIGHT = "light";
  public static final String THEME_DARK = "dark";
  public static final String THEME_SYSTEM = "system";
  public static final String[] THEME_MODES = {THEME_LIGHT, THEME_DARK, THEME_SYSTEM};

  /** Name of the preference that used to hold a look-and-feel class name. */
  private static final String LEGACY_LAF_KEY = "LookAndFeel";

  static {
    migrateLookAndFeelToTheme();
  }

  /**
   * Carries a stored look-and-feel choice over to the new light/dark preference, once.
   *
   * <p>Someone who had picked a dark look and feel should not be handed a light window after
   * updating, and someone who never chose anything should follow their desktop.
   */
  private static void migrateLookAndFeelToTheme() {
    final var prefs = getPrefs();
    if (prefs.get("theme", null) != null) return;
    final var legacy = prefs.get(LEGACY_LAF_KEY, null);
    if (legacy == null) return;
    final var dark = legacy.contains("Dark") || legacy.contains("Darcula");
    prefs.put("theme", dark ? THEME_DARK : THEME_LIGHT);
  }

  /**
   * Whether to use the light theme, the dark theme, or whatever the desktop is set to.
   *
   * <p>Logisim-evolution used to let any installed look and feel be chosen, which meant nothing
   * the application drew itself could be designed to match. There are now two themes of its own.
   */
  public static final PrefMonitor<String> THEME_MODE =
      create(new PrefMonitorStringOpts("theme", THEME_MODES, THEME_SYSTEM));

  public static final String EDITOR_THEME_DEFAULT = "default";
  public static final String EDITOR_THEME_DARK = "dark";
  public static final String[] EDITOR_THEMES = {
    EDITOR_THEME_DEFAULT, EDITOR_THEME_DARK, "monokai", "eclipse", "idea", "vs", "druid"
  };
  public static final PrefMonitor<String> LIGHT_EDITOR_THEME =
      create(
          new PrefMonitorStringOpts(
              "lightEditorTheme", EDITOR_THEMES, EDITOR_THEME_DEFAULT));
  public static final PrefMonitor<String> DARK_EDITOR_THEME =
      create(
          new PrefMonitorStringOpts("darkEditorTheme", EDITOR_THEMES, EDITOR_THEME_DARK));

  public static final PrefMonitor<String> APP_FONT =
      create(new PrefMonitorString("AppFont", ""));

  // The canvas palette. Signal colours keep their meanings — a high signal still reads as
  // "on", an error still reads as wrong — but the saturated green-on-black of the original is
  // replaced by colours chosen to sit beside the rest of the interface.
  // Note the grid now has two levels: DOT is the stronger line every fifth step, ZOOMED_DOT the
  // faint one in between.
  public static final int DEFAULT_CANVAS_BG_COLOR = 0xFFFDFDFE;
  public static final int DEFAULT_GRID_BG_COLOR = 0xFFFDFDFE;
  public static final int DEFAULT_GRID_DOT_COLOR = 0xFFA9B3C4;
  public static final int DEFAULT_ZOOMED_DOT_COLOR = 0xFFDCE2EC;
  // dark mode default grid colors
  public static final int DARK_CANVAS_BG_COLOR = 0xFF1A1C20;
  public static final int DARK_GRID_BG_COLOR = 0xFF1A1C20;
  public static final int DARK_GRID_DOT_COLOR = 0xFF525C6D;
  public static final int DARK_ZOOMED_DOT_COLOR = 0xFF31373F;
  public static final int DEFAULT_COMPONENT_COLOR = 0x00222831;
  public static final int DEFAULT_COMPONENT_SECONDARY_COLOR = 0x99999999;
  public static final int DEFAULT_COMPONENT_GHOST_COLOR = 0x99999999;
  public static final int DEFAULT_COMPONENT_ICON_COLOR = 0x00000000;
  public static final int DEFAULT_TEXT_TOOL_COLOR = 0x00000000;
  // default width-error colors
  public static final int DEFAULT_WIDTH_ERROR_COLOR = 0xFF7B00;
  public static final int DEFAULT_WIDTH_ERROR_CAPTION_COLOR = 0x550000;
  public static final int DEFAULT_WIDTH_ERROR_HIGHLIGHT_COLOR = 0xFFFF00;
  public static final int DEFAULT_WIDTH_ERROR_BACKGROUND_COLOR = 0xFFE6D2;
  // default clock frequency color
  public static final int DEFAULT_CLOCK_FREQUENCY_COLOR = 0xFF00B4;
  // dark mode default component colors
  public static final int DARK_COMPONENT_COLOR = 0xFFE2E6ED;
  public static final int DARK_TEXT_TOOL_COLOR = DARK_COMPONENT_COLOR;
  public static final int DARK_COMPONENT_SECONDARY_COLOR = 0xFFAAAAAA;
  public static final int DARK_COMPONENT_GHOST_COLOR = 0xFF777777;
  public static final int DARK_COMPONENT_ICON_COLOR = 0xFFFFFFFF;
  // expression overline colors
  public static final int DEFAULT_EXPRESSION_OVERLINE_COLOR = 0xFF000000;
  public static final int DARK_EXPRESSION_OVERLINE_COLOR = 0xFFFFFFFF;
  // default kmap cell text colors
  /**
   * The sixteen colours a Karnaugh map draws its covers in.
   *
   * <p>They were sixteen bare hex literals with no dark counterpart, so dark mode faked one by
   * blending every colour 45% toward white — which pushed the dark end of the set together and
   * made covers hard to tell apart, in the one place telling them apart is the point. Two of the
   * light ones, a pale pink and a pale peach, were barely distinguishable from each other as
   * well; {@code KarnaughPaletteTest} now holds both sets to a minimum separation.
   */
  public static final int[] DEFAULT_KMAP_COLORS = {
    0x800000,
    0xE6194B,
    0xFABEBE,
    0xAA6E28,
    0xF58230,
    0xE8C49A,
    0x808000,
    0xFFFF19,
    0xD2F53C,
    0x000080,
    0x911EB4,
    0x3CB4AF,
    0x0082CB,
    0xE6BEFF,
    0xAAFFC3,
    0xF032E6,
  };

  public static final int[] DARK_KMAP_COLORS = {
    0xEA8686,
    0xE86230,
    0xEAD186,
    0xE4E830,
    0xB8EA86,
    0x5BE830,
    0x86EA9F,
    0x30E890,
    0x86EAEA,
    0x30B6E8,
    0x869FEA,
    0x3430E8,
    0xB886EA,
    0xBE30E8,
    0xEA86D1,
    0xE83089,
  };

  public static final int DEFAULT_KMAP_CELL_TEXT_COLOR = 0xFF222831;
  public static final int DARK_KMAP_CELL_TEXT_COLOR = 0xFFE2E6ED;
  // default table caret colors
  public static final int DEFAULT_TABLE_CURSOR_COLOR = 0xFFFFFFFF;
  public static final int DARK_TABLE_CURSOR_COLOR = 0xFF666666;
  public static final int DEFAULT_TABLE_HIGHLIGHT_COLOR = 0xFFFFFFC0;
  public static final int DARK_TABLE_HIGHLIGHT_COLOR = 0xFF7A6A00;
  public static final int DEFAULT_TABLE_SELECTION_COLOR = 0xFFC0C0FF;
  public static final int DARK_TABLE_SELECTION_COLOR = 0xFF323C78;
  // default chronogram (timing diagram) colors
  public static final int DEFAULT_CHRONO_BG_COLOR = 0xFFFFFFFF;
  public static final int DEFAULT_CHRONO_SIGNAL_COLOR = 0xFF000000;
  public static final int DEFAULT_CHRONO_LABEL_COLOR = 0xFF000000;
  public static final int DEFAULT_CHRONO_ROW_BG_COLOR = 0xFFBBBBBB;
  public static final int DEFAULT_CHRONO_SPOT_BG_COLOR = 0xFFAAFFAA;
  // dark mode chronogram colors
  public static final int DARK_CHRONO_BG_COLOR = 0xFF2B2B2B;
  public static final int DARK_CHRONO_SIGNAL_COLOR = 0xFFFFFFFF;
  public static final int DARK_CHRONO_LABEL_COLOR = 0xFFFFFFFF;
  public static final int DARK_CHRONO_ROW_BG_COLOR = 0xFF4A4A4A;
  public static final int DARK_CHRONO_SPOT_BG_COLOR = 0xFF2F5D2F;
  // default fpga board colors
  public static final int DEFAULT_FPGA_BOARD_OUTLINE_COLOR = 0xFF000000;
  public static final int DEFAULT_FPGA_BOARD_TEXT_COLOR = 0xFF0000FF;
  // dark mode fpga board colors
  public static final int DARK_FPGA_BOARD_OUTLINE_COLOR = 0xFFC0C0C0;
  public static final int DARK_FPGA_BOARD_TEXT_COLOR = 0xFF6CB6FF;
  // default FPGA colors
  public static final int DEFAULT_FPGA_DEFINE_COLOR = 0xFF0000;
  public static final int DEFAULT_FPGA_DEFINE_HIGHLIGHT_COLOR = 0x00FF00;
  public static final int DEFAULT_FPGA_DEFINE_RESIZE_COLOR = 0x00FFFF;
  public static final int DEFAULT_FPGA_DEFINE_MOVE_COLOR = 0xFF00FF;
  public static final int DEFAULT_FPGA_MAPPED_COLOR = 0x005000;
  public static final int DEFAULT_FPGA_SELECTED_MAPPED_COLOR = 0xFF0000;
  public static final int DEFAULT_FPGA_SELECTABLE_MAPPED_COLOR = 0x00A000;
  public static final int DEFAULT_FPGA_SELECT_COLOR = 0x0000FF;
  // dark mode default signal colors
  public static final int DARK_TRUE_COLOR = 0xFF3FD68C;
  public static final int DARK_FALSE_COLOR = 0xFF33705A;
  public static final int DARK_UNKNOWN_COLOR = 0xFF9B8CFF;
  public static final int DARK_ERROR_COLOR = 0xFFF2555A;
  public static final int DARK_NIL_COLOR = 0xFF6B7280;
  public static final int DARK_BUS_COLOR = 0xFFD7DCE5;
  public static final int DARK_STROKE_COLOR = 0xFFE6E9F0;
  // default light signal colors
  /**
   * The palette a circuit is drawn in for paper or for an exported image.
   *
   * <p>Its own set rather than the light theme's: a screen is backlit and a sheet is not, and a
   * figure may well be printed or photocopied in grey. Every colour here clears a contrast ratio
   * of three to one against white, which {@code PrintViewColorsTest} holds it to.
   */
  public static final int PRINT_TRUE_COLOR = 0x000A5C34;

  public static final int PRINT_FALSE_COLOR = 0x003E4F48;
  public static final int PRINT_UNKNOWN_COLOR = 0x004A3FB5;
  public static final int PRINT_ERROR_COLOR = 0x00B3272C;
  public static final int PRINT_NIL_COLOR = 0x00424851;
  public static final int PRINT_BUS_COLOR = 0x0021456E;
  public static final int PRINT_STROKE_COLOR = 0x001F2430;

  public static final int DEFAULT_TRUE_COLOR = 0x0018A05B;
  public static final int DEFAULT_FALSE_COLOR = 0x001F5F43;
  public static final int DEFAULT_UNKNOWN_COLOR = 0x006C5CE7;
  public static final int DEFAULT_ERROR_COLOR = 0x00D1373C;
  public static final int DEFAULT_NIL_COLOR = 0x009AA0A6;
  public static final int DEFAULT_BUS_COLOR = 0x002F3542;
  public static final int DEFAULT_STROKE_COLOR = 0x001F2430;

  /**
   * Whether a dark theme is showing.
   *
   * <p>Component drawing asks this in a dozen places to pick a readable colour; it used to be
   * answered by looking for "Dark" in a look-and-feel class name.
   */
  public static boolean isDarkTheme() {
    return Theme.isDark();
  }

  /** Set once the theme-dependent colours have been declared to {@link ThemeColorPrefs}. */
  private static boolean themeColorsRegistered = false;
  private static boolean textToolColorRegistered;

  /** The sixteen cover colours, in order, so they can be registered and reset as a set. */
  @SuppressWarnings("unchecked")
  public static PrefMonitor<Integer>[] kmapColorMonitors() {
    return new PrefMonitor[] {
      KMAP1_COLOR, KMAP2_COLOR, KMAP3_COLOR, KMAP4_COLOR,
      KMAP5_COLOR, KMAP6_COLOR, KMAP7_COLOR, KMAP8_COLOR,
      KMAP9_COLOR, KMAP10_COLOR, KMAP11_COLOR, KMAP12_COLOR,
      KMAP13_COLOR, KMAP14_COLOR, KMAP15_COLOR, KMAP16_COLOR,
    };
  }

  /**
   * Loads the drawing colours belonging to the theme now showing and hands them to {@link Value}.
   *
   * <p>Called at startup and whenever the theme changes. It no longer overwrites the colour
   * preferences: each theme keeps its own set, so a colour the user picked survives switching
   * away and back. See {@link ThemeColorPrefs}.
   */
  public static void applyThemeColors() {
    if (!themeColorsRegistered) {
      themeColorsRegistered = true;
      ThemeColorPrefs.register(CANVAS_BG_COLOR, DEFAULT_CANVAS_BG_COLOR, DARK_CANVAS_BG_COLOR);
      ThemeColorPrefs.register(GRID_BG_COLOR, DEFAULT_GRID_BG_COLOR, DARK_GRID_BG_COLOR);
      ThemeColorPrefs.register(GRID_DOT_COLOR, DEFAULT_GRID_DOT_COLOR, DARK_GRID_DOT_COLOR);
      ThemeColorPrefs.register(GRID_ZOOMED_DOT_COLOR, DEFAULT_ZOOMED_DOT_COLOR, DARK_ZOOMED_DOT_COLOR);
      ThemeColorPrefs.register(COMPONENT_COLOR, DEFAULT_COMPONENT_COLOR, DARK_COMPONENT_COLOR);
      ThemeColorPrefs.register(COMPONENT_SECONDARY_COLOR, DEFAULT_COMPONENT_SECONDARY_COLOR, DARK_COMPONENT_SECONDARY_COLOR);
      ThemeColorPrefs.register(COMPONENT_GHOST_COLOR, DEFAULT_COMPONENT_GHOST_COLOR, DARK_COMPONENT_GHOST_COLOR);
      ThemeColorPrefs.register(COMPONENT_ICON_COLOR, DEFAULT_COMPONENT_ICON_COLOR, DARK_COMPONENT_ICON_COLOR);
      ThemeColorPrefs.register(KMAP_CELL_TEXT_COLOR, DEFAULT_KMAP_CELL_TEXT_COLOR, DARK_KMAP_CELL_TEXT_COLOR);
      final var kmapColors = kmapColorMonitors();
      for (var i = 0; i < kmapColors.length; i++) {
        ThemeColorPrefs.register(kmapColors[i], DEFAULT_KMAP_COLORS[i], DARK_KMAP_COLORS[i]);
      }
      ThemeColorPrefs.register(TABLE_CURSOR_COLOR, DEFAULT_TABLE_CURSOR_COLOR, DARK_TABLE_CURSOR_COLOR);
      ThemeColorPrefs.register(TABLE_HIGHLIGHT_COLOR, DEFAULT_TABLE_HIGHLIGHT_COLOR, DARK_TABLE_HIGHLIGHT_COLOR);
      ThemeColorPrefs.register(TABLE_SELECTION_COLOR, DEFAULT_TABLE_SELECTION_COLOR, DARK_TABLE_SELECTION_COLOR);
      ThemeColorPrefs.register(TRUE_COLOR, DEFAULT_TRUE_COLOR, DARK_TRUE_COLOR);
      ThemeColorPrefs.register(FALSE_COLOR, DEFAULT_FALSE_COLOR, DARK_FALSE_COLOR);
      ThemeColorPrefs.register(UNKNOWN_COLOR, DEFAULT_UNKNOWN_COLOR, DARK_UNKNOWN_COLOR);
      ThemeColorPrefs.register(ERROR_COLOR, DEFAULT_ERROR_COLOR, DARK_ERROR_COLOR);
      ThemeColorPrefs.register(NIL_COLOR, DEFAULT_NIL_COLOR, DARK_NIL_COLOR);
      ThemeColorPrefs.register(BUS_COLOR, DEFAULT_BUS_COLOR, DARK_BUS_COLOR);
      ThemeColorPrefs.register(STROKE_COLOR, DEFAULT_STROKE_COLOR, DARK_STROKE_COLOR);
      ThemeColorPrefs.register(CHRONO_BG_COLOR, DEFAULT_CHRONO_BG_COLOR, DARK_CHRONO_BG_COLOR);
      ThemeColorPrefs.register(CHRONO_SIGNAL_COLOR, DEFAULT_CHRONO_SIGNAL_COLOR, DARK_CHRONO_SIGNAL_COLOR);
      ThemeColorPrefs.register(CHRONO_LABEL_COLOR, DEFAULT_CHRONO_LABEL_COLOR, DARK_CHRONO_LABEL_COLOR);
      ThemeColorPrefs.register(CHRONO_ROW_BG_COLOR, DEFAULT_CHRONO_ROW_BG_COLOR, DARK_CHRONO_ROW_BG_COLOR);
      ThemeColorPrefs.register(CHRONO_SPOT_BG_COLOR, DEFAULT_CHRONO_SPOT_BG_COLOR, DARK_CHRONO_SPOT_BG_COLOR);
    }
    ThemeColorPrefs.migrateShippedPalette();
    // Register after the legacy palette migration, which must not discard text overrides.
    if (!textToolColorRegistered) {
      migrateTextToolColor();
      ThemeColorPrefs.register(TEXT_TOOL_COLOR, DEFAULT_TEXT_TOOL_COLOR, DARK_TEXT_TOOL_COLOR);
      textToolColorRegistered = true;
    }
    ThemeColorPrefs.applyTheme(isDarkTheme());
    reloadValueColors();
  }

  /** Retains an explicitly stored legacy text colour in both palettes, including explicit black. */
  static void migrateTextToolColor() {
    final var prefs = getPrefs();
    if (prefs.getBoolean("textToolThemeMigrated", false)) return;
    final var key = TEXT_TOOL_COLOR.getIdentifier();
    final var legacy = prefs.get(key, null);
    if (legacy != null) {
      for (final var prefix : ThemeColorPrefs.storagePrefixes()) {
        if (prefs.get(prefix + key, null) == null) prefs.put(prefix + key, legacy);
      }
    }
    prefs.putBoolean("textToolThemeMigrated", true);
  }

  /** Copies the colour preferences into the colours {@link Value} paints with. */
  static void reloadValueColors() {
    Value.reloadColors();
  }

  /** Puts the shipped colours back, for the theme showing now only. */
  public static void setDefaultGridColors() {
    applyThemeColors();
    ThemeColorPrefs.resetCurrentTheme();
    reloadValueColors();
  }

  public static final PrefMonitor<Integer> CANVAS_BG_COLOR =
      create(new PrefMonitorInt("canvasBgColor", DEFAULT_CANVAS_BG_COLOR));
  public static final PrefMonitor<Integer> GRID_BG_COLOR =
      create(new PrefMonitorInt("gridBgColor", DEFAULT_GRID_BG_COLOR));
  public static final PrefMonitor<Integer> GRID_DOT_COLOR =
      create(new PrefMonitorInt("gridDotColor", DEFAULT_GRID_DOT_COLOR));
  public static final PrefMonitor<Integer> GRID_ZOOMED_DOT_COLOR =
      create(new PrefMonitorInt("gridZoomedDotColor", DEFAULT_ZOOMED_DOT_COLOR));
  public static final PrefMonitor<Integer> COMPONENT_COLOR =
      create(new PrintViewColorPreference("componentColor", DEFAULT_COMPONENT_COLOR));
  public static final PrefMonitor<Integer> COMPONENT_SECONDARY_COLOR =
      create(
          new PrintViewColorPreference(
              "componentSecondaryColor", DEFAULT_COMPONENT_SECONDARY_COLOR));
  public static final PrefMonitor<Integer> COMPONENT_GHOST_COLOR =
      create(new PrintViewColorPreference("componentGhostColor", DEFAULT_COMPONENT_GHOST_COLOR));
  public static final PrefMonitor<Integer> COMPONENT_ICON_COLOR =
      create(new PrintViewColorPreference("componentIconColor", DEFAULT_COMPONENT_ICON_COLOR));
  public static final PrefMonitor<Integer> TEXT_TOOL_COLOR =
      create(new PrefMonitorInt("textToolColor", DEFAULT_TEXT_TOOL_COLOR));
  public static final PrefMonitor<Integer> CHRONO_BG_COLOR =
      create(new PrefMonitorInt("chronoBgColor", DEFAULT_CHRONO_BG_COLOR));
  public static final PrefMonitor<Integer> CHRONO_SIGNAL_COLOR =
      create(new PrefMonitorInt("chronoSignalColor", DEFAULT_CHRONO_SIGNAL_COLOR));
  public static final PrefMonitor<Integer> CHRONO_LABEL_COLOR =
      create(new PrefMonitorInt("chronoLabelColor", DEFAULT_CHRONO_LABEL_COLOR));
  public static final PrefMonitor<Integer> CHRONO_ROW_BG_COLOR =
      create(new PrefMonitorInt("chronoRowBgColor", DEFAULT_CHRONO_ROW_BG_COLOR));
  public static final PrefMonitor<Integer> CHRONO_SPOT_BG_COLOR =
      create(new PrefMonitorInt("chronoSpotBgColor", DEFAULT_CHRONO_SPOT_BG_COLOR));


  // Layout preferences
  public static final String ADD_AFTER_UNCHANGED = "unchanged";
  public static final String ADD_AFTER_EDIT = "edit";
  public static final PrefMonitor<Boolean> ATTRIBUTE_HALO =
      create(new PrefMonitorBoolean("attributeHalo", true));

  /** Components the user has pinned to the top of the palette, encoded by {@code PaletteMemory}. */
  public static final PrefMonitor<String> PALETTE_FAVOURITES =
      create(new PrefMonitorString("paletteFavourites", ""));

  /** The components most recently placed, most recent first. */
  public static final PrefMonitor<String> PALETTE_RECENTS =
      create(new PrefMonitorString("paletteRecents", ""));

  /** Whether the component panel shows the library tree rather than the palette of tiles. */
  public static final PrefMonitor<Boolean> PALETTE_SHOW_TREE =
      create(new PrefMonitorBoolean("paletteShowTree", false));
  public static final PrefMonitor<Boolean> COMPONENT_TIPS =
      create(new PrefMonitorBoolean("componentTips", true));
  public static final PrefMonitor<Boolean> MOVE_KEEP_CONNECT =
      create(new PrefMonitorBoolean("keepConnected", true));
  public static final PrefMonitor<Boolean> ADD_SHOW_GHOSTS =
      create(new PrefMonitorBoolean("showGhosts", true));
  public static final PrefMonitor<Boolean> NAMED_CIRCUIT_BOXES_FIXED_SIZE =
      create(new PrefMonitorBoolean("namedBoxesFixed", true));
  public static final PrefMonitor<Boolean> KMAP_LINED_STYLE =
      create(new PrefMonitorBoolean("KmapLinedStyle", false));
  public static final PrefMonitor<String> DefaultAppearance =
      create(
          new PrefMonitorStringOpts(
              "defaultAppearance",
              new String[] {
                  StdAttr.APPEAR_CLASSIC.toString(),
                  StdAttr.APPEAR_FPGA.toString(),
                  StdAttr.APPEAR_EVOLUTION.toString()
              },
              StdAttr.APPEAR_EVOLUTION.toString()));

  public static AttributeOption getDefaultAppearance() {
    if (DefaultAppearance.get().equals(StdAttr.APPEAR_EVOLUTION.toString())) {
      return StdAttr.APPEAR_EVOLUTION;
    } else {
      return StdAttr.APPEAR_CLASSIC;
    }
  }

  public static AttributeOption getDefaultCircuitAppearance() {
    if (DefaultAppearance.get().equals(StdAttr.APPEAR_EVOLUTION.toString())) {
      return StdAttr.APPEAR_EVOLUTION;
    } else if (DefaultAppearance.get().equals(StdAttr.APPEAR_FPGA.toString())) {
      return StdAttr.APPEAR_FPGA;
    } else {
      return StdAttr.APPEAR_CLASSIC;
    }
  }

  public static final PrefMonitor<Boolean> NEW_INPUT_OUTPUT_SHAPES =
      create(new PrefMonitorBooleanConvert("oldIO", true));

  /**
   * Returns the scale the platform already applies to the interface.
   *
   * <p>On a display the operating system scales, Java2D scales the whole interface itself, so this
   * is greater than one and the application must not scale a second time.
   */
  public static double getPlatformScaleFactor() {
    if (GraphicsEnvironment.isHeadless()) return 1.0;
    try {
      final var transform =
          GraphicsEnvironment.getLocalGraphicsEnvironment()
              .getDefaultScreenDevice()
              .getDefaultConfiguration()
              .getDefaultTransform();
      final var scale = transform.getScaleX();
      return (scale > 0.0) ? scale : 1.0;
    } catch (Exception ignored) {
      // Some headless-like environments expose no usable screen device.
      return 1.0;
    }
  }

  /**
   * Returns the interface scale to use when the user has not chosen one.
   *
   * <p>FlatLaf resolves platform/font scaling independently of Java2D's device transform. Exclude
   * application zoom so that returning to Auto cannot compound a previous explicit setting. A
   * cached desktop DPI fills in missing Xwayland scaling after the background probe completes.
   */
  public static double getAutoScaleFactor() {
    return DesktopScale.autoFactor(
        UIScale.getUserScaleFactor() / UIScale.getZoomFactor(), getPlatformScaleFactor());
  }

  /** The legacy value, exposed for the development scale probe. */
  public static double getLegacyAutoScaleFactorForProbe() {
    return getLegacyAutoScaleFactor();
  }

  /** Returns the scale the old screen-height heuristic would have produced, for migration. */
  static double getLegacyAutoScaleFactor() {
    return (GraphicsEnvironment.isHeadless()
            ? 0.0
            : Toolkit.getDefaultToolkit().getScreenSize().getHeight())
        / 1000.0;
  }

  public static final PrefMonitor<Double> SCALE_FACTOR =
      create(
          new PrefMonitorDouble(
              "Scale",
              Math.max(
                  getAutoScaleFactor(),
                  1.0)));

  public static final PrefMonitor<String> ADD_AFTER =
      create(
          new PrefMonitorStringOpts(
              "afterAdd", new String[] {ADD_AFTER_EDIT, ADD_AFTER_UNCHANGED}, ADD_AFTER_EDIT));

  public static final String PIN_APPEAR_DOT_SMALL = "dot-small";
  public static final String PIN_APPEAR_DOT_MEDIUM = "dot-medium";
  public static final String PIN_APPEAR_DOT_BIG = "dot-big";
  public static final String PIN_APPEAR_DOT_BIGGER = "dot-bigger";
  public static final PrefMonitor<String> PinAppearance =
      create(
          new PrefMonitorStringOpts(
              "pinAppearance",
              new String[] {
                  PIN_APPEAR_DOT_SMALL,
                  PIN_APPEAR_DOT_MEDIUM,
                  PIN_APPEAR_DOT_BIG,
                  PIN_APPEAR_DOT_BIGGER
              },
              PIN_APPEAR_DOT_SMALL));

  public static final PrefMonitor<String> POKE_WIRE_RADIX1;
  public static final PrefMonitor<String> POKE_WIRE_RADIX2;

  static {
    final var radixOptions = RadixOption.OPTIONS;
    final var radixStrings = new String[radixOptions.length];
    for (var i = 0; i < radixOptions.length; i++) {
      radixStrings[i] = radixOptions[i].getSaveString();
    }
    POKE_WIRE_RADIX1 =
        create(
            new PrefMonitorStringOpts(
                "pokeRadix1", radixStrings, RadixOption.RADIX_2.getSaveString()));
    POKE_WIRE_RADIX2 =
        create(
            new PrefMonitorStringOpts(
                "pokeRadix2", radixStrings, RadixOption.RADIX_10_SIGNED.getSaveString()));
  }

  public static final PrefMonitor<Boolean> Memory_Startup_Unknown =
      create(new PrefMonitorBoolean("MemStartUnknown", false));

  // Simulation preferences
  public static final PrefMonitor<Integer> TRUE_COLOR =
      create(new PrefMonitorInt("SimTrueColor", DEFAULT_TRUE_COLOR));
  public static final PrefMonitor<String> TRUE_CHAR =
      create(new PrefMonitorString("SimTrueChar", "1 "));
  public static final PrefMonitor<Integer> FALSE_COLOR =
      create(new PrefMonitorInt("SimFalseColor", DEFAULT_FALSE_COLOR));
  public static final PrefMonitor<String> FALSE_CHAR =
      create(new PrefMonitorString("SimFalseChar", "0 "));
  public static final PrefMonitor<Integer> UNKNOWN_COLOR =
      create(new PrefMonitorInt("SimUnknownColor", DEFAULT_UNKNOWN_COLOR));
  public static final PrefMonitor<String> UNKNOWN_CHAR =
      create(new PrefMonitorString("SimUnknownChar", "U "));
  public static final PrefMonitor<Integer> ERROR_COLOR =
      create(new PrefMonitorInt("SimErrorColor", DEFAULT_ERROR_COLOR));
  public static final PrefMonitor<String> ERROR_CHAR =
      create(new PrefMonitorString("SimErrorChar", "E "));
  public static final PrefMonitor<Integer> NIL_COLOR =
      create(new PrefMonitorInt("SimNilColor", DEFAULT_NIL_COLOR));
  public static final PrefMonitor<String> DONTCARE_CHAR =
      create(new PrefMonitorString("SimDontCareChar", "- "));
  public static final PrefMonitor<Integer> BUS_COLOR = create(new PrefMonitorInt("SimBusColor", DEFAULT_BUS_COLOR));
  public static final PrefMonitor<Integer> STROKE_COLOR =
      create(new PrefMonitorInt("SimStrokeColor", DEFAULT_STROKE_COLOR));
  public static final PrefMonitor<Integer> WIDTH_ERROR_COLOR =
      create(new PrefMonitorInt("SimWidthErrorColor", DEFAULT_WIDTH_ERROR_COLOR));
  public static final PrefMonitor<Integer> WIDTH_ERROR_CAPTION_COLOR =
      create(new PrefMonitorInt("SimWidthErrorCaptionColor", DEFAULT_WIDTH_ERROR_CAPTION_COLOR));
  public static final PrefMonitor<Integer> WIDTH_ERROR_HIGHLIGHT_COLOR =
      create(new PrefMonitorInt("SimWidthErrorHighlightColor", DEFAULT_WIDTH_ERROR_HIGHLIGHT_COLOR));
  public static final PrefMonitor<Integer> WIDTH_ERROR_BACKGROUND_COLOR =
      create(new PrefMonitorInt("SimWidthErrorBackgroundColor", DEFAULT_WIDTH_ERROR_BACKGROUND_COLOR));
  public static final PrefMonitor<Integer> CLOCK_FREQUENCY_COLOR =
      create(new PrefMonitorInt("SimClockFrequencyColor", DEFAULT_CLOCK_FREQUENCY_COLOR));
  public static final PrefMonitor<Integer> KMAP_CELL_TEXT_COLOR =
      create(new PrefMonitorInt("KmapCellTextColor", DEFAULT_KMAP_CELL_TEXT_COLOR));
  public static final PrefMonitor<Integer> TABLE_CURSOR_COLOR =
      create(new PrefMonitorInt("TableCursorColor", DEFAULT_TABLE_CURSOR_COLOR));
  public static final PrefMonitor<Integer> TABLE_HIGHLIGHT_COLOR =
      create(new PrefMonitorInt("TableHighlightColor", DEFAULT_TABLE_HIGHLIGHT_COLOR));
  public static final PrefMonitor<Integer> TABLE_SELECTION_COLOR =
      create(new PrefMonitorInt("TableSelectionColor", DEFAULT_TABLE_SELECTION_COLOR));
  public static final PrefMonitor<Integer> KMAP1_COLOR =
      create(new PrefMonitorInt("KMAPColor1", DEFAULT_KMAP_COLORS[0]));
  public static final PrefMonitor<Integer> KMAP2_COLOR =
      create(new PrefMonitorInt("KMAPColor2", DEFAULT_KMAP_COLORS[1]));
  public static final PrefMonitor<Integer> KMAP3_COLOR =
      create(new PrefMonitorInt("KMAPColor3", DEFAULT_KMAP_COLORS[2]));
  public static final PrefMonitor<Integer> KMAP4_COLOR =
      create(new PrefMonitorInt("KMAPColor4", DEFAULT_KMAP_COLORS[3]));
  public static final PrefMonitor<Integer> KMAP5_COLOR =
      create(new PrefMonitorInt("KMAPColor5", DEFAULT_KMAP_COLORS[4]));
  public static final PrefMonitor<Integer> KMAP6_COLOR =
      create(new PrefMonitorInt("KMAPColor6", DEFAULT_KMAP_COLORS[5]));
  public static final PrefMonitor<Integer> KMAP7_COLOR =
      create(new PrefMonitorInt("KMAPColor7", DEFAULT_KMAP_COLORS[6]));
  public static final PrefMonitor<Integer> KMAP8_COLOR =
      create(new PrefMonitorInt("KMAPColor8", DEFAULT_KMAP_COLORS[7]));
  public static final PrefMonitor<Integer> KMAP9_COLOR =
      create(new PrefMonitorInt("KMAPColor9", DEFAULT_KMAP_COLORS[8]));
  public static final PrefMonitor<Integer> KMAP10_COLOR =
      create(new PrefMonitorInt("KMAPColor10", DEFAULT_KMAP_COLORS[9]));
  public static final PrefMonitor<Integer> KMAP11_COLOR =
      create(new PrefMonitorInt("KMAPColor11", DEFAULT_KMAP_COLORS[10]));
  public static final PrefMonitor<Integer> KMAP12_COLOR =
      create(new PrefMonitorInt("KMAPColor12", DEFAULT_KMAP_COLORS[11]));
  public static final PrefMonitor<Integer> KMAP13_COLOR =
      create(new PrefMonitorInt("KMAPColor13", DEFAULT_KMAP_COLORS[12]));
  public static final PrefMonitor<Integer> KMAP14_COLOR =
      create(new PrefMonitorInt("KMAPColor14", DEFAULT_KMAP_COLORS[13]));
  public static final PrefMonitor<Integer> KMAP15_COLOR =
      create(new PrefMonitorInt("KMAPColor15", DEFAULT_KMAP_COLORS[14]));
  public static final PrefMonitor<Integer> KMAP16_COLOR =
      create(new PrefMonitorInt("KMAPColor16", DEFAULT_KMAP_COLORS[15]));

  // FPGA Commander colors
  public static final PrefMonitor<Integer> FPGA_DEFINE_COLOR =
      create(new PrefMonitorInt("FPGADefineColor", DEFAULT_FPGA_DEFINE_COLOR));
  public static final PrefMonitor<Integer> FPGA_DEFINE_HIGHLIGHT_COLOR =
      create(new PrefMonitorInt("FPGADefineHighlightColor", DEFAULT_FPGA_DEFINE_HIGHLIGHT_COLOR));
  public static final PrefMonitor<Integer> FPGA_DEFINE_RESIZE_COLOR =
      create(new PrefMonitorInt("FPGADefineResizeColor", DEFAULT_FPGA_DEFINE_RESIZE_COLOR));
  public static final PrefMonitor<Integer> FPGA_DEFINE_MOVE_COLOR =
      create(new PrefMonitorInt("FPGADefineMoveColor", DEFAULT_FPGA_DEFINE_MOVE_COLOR));
  public static final PrefMonitor<Integer> FPGA_MAPPED_COLOR =
      create(new PrefMonitorInt("FPGAMappedColor", DEFAULT_FPGA_MAPPED_COLOR));
  public static final PrefMonitor<Integer> FPGA_SELECTED_MAPPED_COLOR =
      create(new PrefMonitorInt("FPGASelectedMappedColor", DEFAULT_FPGA_SELECTED_MAPPED_COLOR));
  public static final PrefMonitor<Integer> FPGA_SELECTABLE_MAPPED_COLOR =
      create(new PrefMonitorInt("FPGASelectableMappedColor", DEFAULT_FPGA_SELECTABLE_MAPPED_COLOR));
  public static final PrefMonitor<Integer> FPGA_SELECT_COLOR =
      create(new PrefMonitorInt("FPGASelectColor", DEFAULT_FPGA_SELECT_COLOR));

  // Experimental preferences
  public static final String ACCEL_DEFAULT = "default";

  public static final String ACCEL_NONE = "none";

  public static final String ACCEL_OPENGL = "opengl";

  public static final String ACCEL_D3D = "d3d";

  public static final String ACCEL_METAL = "metal";

  public static final PrefMonitor<String> GRAPHICS_ACCELERATION =
      create(
          new PrefMonitorStringOpts(
              "graphicsAcceleration",
              new String[] {ACCEL_DEFAULT, ACCEL_NONE, ACCEL_OPENGL, ACCEL_D3D, ACCEL_METAL},
              ACCEL_DEFAULT));

  public static final String SIM_QUEUE_DEFAULT = "default";
  public static final String SIM_QUEUE_PRIORITY = "priority";
  public static final String SIM_QUEUE_SPLAY = "splay";
  public static final String SIM_QUEUE_LINKED = "linked";
  public static final String SIM_QUEUE_LIST_OF_QUEUES = "listOfQueues";
  public static final String SIM_QUEUE_TREE_OF_QUEUES = "treeOfQueues";
  public static final PrefMonitor<String> SIMULATION_QUEUE =
      create(
          new PrefMonitorStringOpts("simQueue",
              new String[] {SIM_QUEUE_DEFAULT, SIM_QUEUE_PRIORITY, SIM_QUEUE_SPLAY,
                            SIM_QUEUE_LINKED, SIM_QUEUE_LIST_OF_QUEUES, SIM_QUEUE_TREE_OF_QUEUES},
              SIM_QUEUE_DEFAULT)
      );
  public static final PrefMonitor<Boolean> AntiAliassing =
      create(new PrefMonitorBoolean("AntiAliassing", true));
  public static final PrefMonitor<Boolean> UI_ANTIALIASING =
      create(new PrefMonitorBoolean("uiAntiAliasing", true));

  // Third party softwares preferences
  public static final PrefMonitor<String> QUESTA_PATH =
      create(new PrefMonitorString("questaPath", ""));

  public static final PrefMonitor<Boolean> QUESTA_VALIDATION =
      create(new PrefMonitorBoolean("questaValidation", false));
  public static final String VHDL_STANDARD_1993 = "1993";
  public static final String VHDL_STANDARD_2002 = "2002";
  public static final String VHDL_STANDARD_2008 = "2008";
  public static final PrefMonitor<String> VHDL_STANDARD =
      create(
          new PrefMonitorStringOpts(
              "vhdlStandard",
              new String[] {VHDL_STANDARD_1993, VHDL_STANDARD_2002, VHDL_STANDARD_2008},
              VHDL_STANDARD_2002));
  public static final PrefMonitor<String> QuartusToolPath =
      create(new PrefMonitorString("QuartusToolPath", ""));
  public static final PrefMonitor<String> ISEToolPath =
      create(new PrefMonitorString("ISEToolPath", ""));
  public static final PrefMonitor<String> VivadoToolPath =
      create(new PrefMonitorString("VivadoToolPath", ""));
  public static final PrefMonitor<String> OpenFpgaToolPath =
      create(new PrefMonitorString("OpenFpgaToolPath", ""));

  // hidden window preferences - not part of the preferences dialog, changes
  // to preference does not affect current windows, and the values are not
  // saved until the application is closed
  public static final String RECENT_PROJECTS = "recentProjects";

  private static final RecentProjects recentProjects = new RecentProjects();

  public static final PrefMonitor<Double> TICK_FREQUENCY =
      create(new PrefMonitorDouble("tickFrequency", 1.0));

  public static final PrefMonitor<Boolean> LAYOUT_SHOW_GRID =
      create(new PrefMonitorBoolean("layoutGrid", true));

  public static final PrefMonitor<Double> LAYOUT_ZOOM =
      create(new PrefMonitorDouble("layoutZoom", 1.0));

  public static final PrefMonitor<Boolean> APPEARANCE_SHOW_GRID =
      create(new PrefMonitorBoolean("appearanceGrid", true));

  public static final PrefMonitor<Double> APPEARANCE_ZOOM =
      create(new PrefMonitorDouble("appearanceZoom", 1.0));

  public static final PrefMonitor<Integer> WINDOW_STATE =
      create(new PrefMonitorInt("windowState", JFrame.NORMAL));

  public static final PrefMonitor<Integer> WINDOW_WIDTH =
      create(
          new PrefMonitorInt(
              "windowWidth",
              ((!GraphicsEnvironment.isHeadless())
                  ? Toolkit.getDefaultToolkit().getScreenSize().width
                  : 0)
                  / 2));

  public static final PrefMonitor<Integer> WINDOW_HEIGHT =
      create(
          new PrefMonitorInt(
              "windowHeight",
              ((!GraphicsEnvironment.isHeadless())
                  ? Toolkit.getDefaultToolkit().getScreenSize().height
                  : 0)));

  public static void resetWindow() {
    WINDOW_EXPLORER_VISIBLE.set(true);
    WINDOW_MAIN_SPLIT.set(0.251);
    WINDOW_LEFT_SPLIT.set(0.51);
    WINDOW_RIGHT_SPLIT.set(0.751);
  }

  public static final PrefMonitor<String> WINDOW_LOCATION =
      create(new PrefMonitorString("windowLocation", "0,0"));

  public static final PrefMonitor<Double> WINDOW_MAIN_SPLIT =
      create(new PrefMonitorDouble("windowMainSplit", 0.25));

  public static final PrefMonitor<Boolean> WINDOW_EXPLORER_VISIBLE =
      create(new PrefMonitorBoolean("windowExplorerVisible", true));

  public static final PrefMonitor<Double> WINDOW_LEFT_SPLIT =
      create(new PrefMonitorDouble("windowLeftSplit", 0.5));

  public static final PrefMonitor<Double> WINDOW_RIGHT_SPLIT =
      create(new PrefMonitorDouble("windowRightSplit", 0.75));

  public static final PrefMonitor<String> DIALOG_DIRECTORY =
      create(new PrefMonitorString("dialogDirectory", ""));

  /* Opens the action search when Shift is tapped twice in quick succession. Kept switchable
   * because Shift is a working modifier on the canvas, so the gesture can misfire. */
  public static final PrefMonitor<Boolean> SEARCH_DOUBLE_SHIFT =
      create(new PrefMonitorBoolean("searchDoubleShift", true));

  /* Hotkey Settings */
  /* Watch whether in headless mode */
  public static final int hotkeyMenuMask =
      GraphicsEnvironment.isHeadless()
          ? InputEvent.ALT_DOWN_MASK : new JMenu().getToolkit().getMenuShortcutKeyMaskEx();
  public static final PrefMonitor<KeyStroke> HOTKEY_SIM_AUTO_PROPAGATE =
      create(new PrefMonitorKeyStroke("hotkeySimAutoPropagate", KeyEvent.VK_E, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_SIM_RESET =
      create(new PrefMonitorKeyStroke("hotkeySimReset", KeyEvent.VK_R, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_SIM_STEP =
      create(new PrefMonitorKeyStroke("hotkeySimStep", KeyEvent.VK_I, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_SIM_TICK_HALF =
      create(new PrefMonitorKeyStroke("hotkeySimTickHalf", KeyEvent.VK_T, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_SIM_TICK_FULL =
      create(new PrefMonitorKeyStroke("hotkeySimTickFull", KeyEvent.VK_F9, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_SIM_TICK_ENABLED =
      create(new PrefMonitorKeyStroke("hotkeySimTickEnabled", KeyEvent.VK_K, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_UNDO =
      create(new PrefMonitorKeyStroke("hotkeyEditUndo", KeyEvent.VK_Z, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_REDO =
      create(new PrefMonitorKeyStroke("hotkeyEditRedo",
          KeyEvent.VK_Z, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_CUT =
      create(new PrefMonitorKeyStroke("hotkeyEditCut", KeyEvent.VK_X, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_COPY =
      create(new PrefMonitorKeyStroke("hotkeyEditCopy", KeyEvent.VK_C, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_PASTE =
      create(new PrefMonitorKeyStroke("hotkeyEditPaste", KeyEvent.VK_V, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_DELETE =
      create(new PrefMonitorKeyStroke("hotkeyEditDelete", KeyEvent.VK_DELETE, 0, false, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_SELECT_ALL =
      create(new PrefMonitorKeyStroke("hotkeyEditSelectAll", KeyEvent.VK_A, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_RAISE =
      create(new PrefMonitorKeyStroke("hotkeyEditRaise", KeyEvent.VK_UP, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_LOWER =
      create(new PrefMonitorKeyStroke("hotkeyEditLower", KeyEvent.VK_DOWN, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_RAISE_TOP =
      create(new PrefMonitorKeyStroke("hotkeyEditRaiseTop", KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_LOWER_BOTTOM =
      create(new PrefMonitorKeyStroke("hotkeyEditLowerBottom", KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_WINDOW_CLOSE =
      create(new PrefMonitorKeyStroke("hotkeyWindowClose",
          KeyEvent.VK_W, hotkeyMenuMask | InputEvent.SHIFT_DOWN_MASK,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_WINDOW_MINIMIZE =
      create(new PrefMonitorKeyStroke("hotkeyWindowMinimize",
          KeyEvent.VK_M, hotkeyMenuMask | InputEvent.SHIFT_DOWN_MASK,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_FILE_EXPORT =
      create(new PrefMonitorKeyStroke("hotkeyFileExport",
          KeyEvent.VK_E, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_FILE_PRINT =
      create(new PrefMonitorKeyStroke("hotkeyFilePrint", KeyEvent.VK_P, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_FILE_NEW =
      create(new PrefMonitorKeyStroke("hotkeyFileNew", KeyEvent.VK_N, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_FILE_MERGE =
      create(new PrefMonitorKeyStroke("hotkeyFileMerge", KeyEvent.VK_M, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_FILE_OPEN =
      create(new PrefMonitorKeyStroke("hotkeyFileOpen", KeyEvent.VK_O, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_FILE_SAVE =
      create(new PrefMonitorKeyStroke("hotkeyFileSave", KeyEvent.VK_S, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_FILE_SAVE_AS =
      create(new PrefMonitorKeyStroke("hotkeyFileSaveAs", KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_FILE_PREFERENCES =
      create(new PrefMonitorKeyStroke("hotkeyFilePreferences", KeyEvent.VK_COMMA, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_FILE_QUIT =
      create(new PrefMonitorKeyStroke("hotkeyFileQuit", KeyEvent.VK_Q, hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_SEARCH =
      create(new PrefMonitorKeyStroke("hotkeySearch",
          KeyEvent.VK_A, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_1 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect1", KeyEvent.VK_1, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_2 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect2", KeyEvent.VK_2, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_3 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect3", KeyEvent.VK_3, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_4 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect4", KeyEvent.VK_4, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_5 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect5", KeyEvent.VK_5, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_6 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect6", KeyEvent.VK_6, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_7 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect7", KeyEvent.VK_7, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_8 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect8", KeyEvent.VK_8, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_9 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect9", KeyEvent.VK_9, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_10 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect10", KeyEvent.VK_0, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_11 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect11", null, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_12 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect12", null, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_13 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect13", null, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_TOOL_SELECT_14 =
      create(new PrefMonitorKeyStroke("hotkeyToolSelect14", null, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_DIR_NORTH =
      create(new PrefMonitorKeyStroke("hotkeyDirNorth", KeyEvent.VK_UP, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_DIR_SOUTH =
      create(new PrefMonitorKeyStroke("hotkeyDirSouth", KeyEvent.VK_DOWN, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_DIR_EAST =
      create(new PrefMonitorKeyStroke("hotkeyDirEast", KeyEvent.VK_RIGHT, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_DIR_WEST =
      create(new PrefMonitorKeyStroke("hotkeyDirWest", KeyEvent.VK_LEFT, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_MENU_DUPLICATE =
      create(new PrefMonitorKeyStroke("hotkeyEditMenuDuplicate", KeyEvent.VK_D, hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_EDIT_TOOL_DUPLICATE =
      create(new PrefMonitorKeyStroke("hotkeyEditToolDuplicate", KeyEvent.VK_INSERT, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_PROJ_MOVE_UP =
      create(new PrefMonitorKeyStroke("hotkeyProjMoveUp",
          KeyEvent.VK_U, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_PROJ_MOVE_DOWN =
      create(new PrefMonitorKeyStroke("hotkeyProjMoveDown",
          KeyEvent.VK_D, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask,
          true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_PROJ_ADD_CIRCUIT =
      create(new PrefMonitorKeyStroke("hotkeyProjAddCircuit", KeyEvent.VK_N, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_PROJ_ANALYZE =
      create(new PrefMonitorKeyStroke("hotkeyProjAnalyze", KeyEvent.VK_T, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_PROJ_STATS =
      create(new PrefMonitorKeyStroke("hotkeyProjStats", KeyEvent.VK_I, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_PROJ_OPTIONS =
      create(new PrefMonitorKeyStroke("hotkeyProjOptions", KeyEvent.VK_O, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask, true, true));

  public static final PrefMonitor<KeyStroke> HOTKEY_AUTO_LABEL_OPEN =
      create(new PrefMonitorKeyStroke("hotkeyAutoLabelOpen", KeyEvent.VK_L, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_AUTO_LABEL_TOGGLE =
      create(new PrefMonitorKeyStroke("hotkeyAutoLabelToggle", KeyEvent.VK_T, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_AUTO_LABEL_VIEW =
      create(new PrefMonitorKeyStroke("hotkeyAutoLabelView", KeyEvent.VK_V, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_AUTO_LABEL_HIDE =
      create(new PrefMonitorKeyStroke("hotkeyAutoLabelHide", KeyEvent.VK_H, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_AUTO_LABEL_SELF_NUMBERED_STOP =
      create(new PrefMonitorKeyStroke("hotkeyAutoLabelSelfNumberedStop", KeyEvent.VK_A, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_ADD_TOOL_ROTATE =
      create(new PrefMonitorKeyStroke("hotkeyAddToolRotate", KeyEvent.VK_R, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_GATE_MODIFIER_SIZE_SMALL =
      create(new PrefMonitorKeyStroke("hotkeyGateModifierSizeSmall", new KeyStroke[] {
          KeyStroke.getKeyStroke(KeyEvent.VK_S, 0),
          KeyStroke.getKeyStroke(KeyEvent.VK_N, 0),
      }));

  public static final PrefMonitor<KeyStroke> HOTKEY_GATE_MODIFIER_SIZE_MEDIUM =
      create(new PrefMonitorKeyStroke("hotkeyGateModifierSizeMedium", KeyEvent.VK_M, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_GATE_MODIFIER_SIZE_WIDE =
      create(new PrefMonitorKeyStroke("hotkeyGateModifierSizeWide", KeyEvent.VK_W, 0));

  public static final PrefMonitor<KeyStroke> HOTKEY_GATE_MODIFIER_INPUT_ADD =
      create(new PrefMonitorKeyStroke("hotkeyGateModifierInputAdd", new KeyStroke[] {
          KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0),
          KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0),
          KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0),
      }));

  public static final PrefMonitor<KeyStroke> HOTKEY_GATE_MODIFIER_INPUT_SUB =
      create(new PrefMonitorKeyStroke("hotkeyGateModifierInputSub", new KeyStroke[] {
          KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0),
          KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0),
      }));

  public static void resetHotkeys() {
    try {
      int menuMask = hotkeyMenuMask;
      HOTKEY_SIM_AUTO_PROPAGATE.set(KeyStroke.getKeyStroke(KeyEvent.VK_E, menuMask));
      HOTKEY_SIM_RESET.set(KeyStroke.getKeyStroke(KeyEvent.VK_R, menuMask));
      HOTKEY_SIM_STEP.set(KeyStroke.getKeyStroke(KeyEvent.VK_I, menuMask));
      HOTKEY_SIM_TICK_HALF.set(KeyStroke.getKeyStroke(KeyEvent.VK_T, menuMask));
      HOTKEY_SIM_TICK_FULL.set(KeyStroke.getKeyStroke(KeyEvent.VK_F9, menuMask));
      HOTKEY_SIM_TICK_ENABLED.set(KeyStroke.getKeyStroke(KeyEvent.VK_K, menuMask));
      HOTKEY_EDIT_UNDO.set(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask));
      HOTKEY_EDIT_REDO.set(KeyStroke.getKeyStroke(KeyEvent.VK_Z,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_WINDOW_CLOSE.set(KeyStroke.getKeyStroke(KeyEvent.VK_W,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_WINDOW_MINIMIZE.set(KeyStroke.getKeyStroke(KeyEvent.VK_M,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_EDIT_CUT.set(KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask));
      HOTKEY_EDIT_COPY.set(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask));
      HOTKEY_EDIT_PASTE.set(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
      HOTKEY_EDIT_DELETE.set(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
      HOTKEY_EDIT_SELECT_ALL.set(KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask));
      HOTKEY_EDIT_RAISE.set(KeyStroke.getKeyStroke(KeyEvent.VK_UP, menuMask));
      HOTKEY_EDIT_LOWER.set(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, menuMask));
      HOTKEY_EDIT_RAISE_TOP.set(KeyStroke.getKeyStroke(KeyEvent.VK_UP,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_EDIT_LOWER_BOTTOM.set(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_FILE_NEW.set(KeyStroke.getKeyStroke(KeyEvent.VK_N, menuMask));
      HOTKEY_FILE_MERGE.set(KeyStroke.getKeyStroke(KeyEvent.VK_M, menuMask));
      HOTKEY_FILE_OPEN.set(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask));
      HOTKEY_FILE_SAVE.set(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask));
      HOTKEY_FILE_SAVE_AS.set(KeyStroke.getKeyStroke(KeyEvent.VK_S,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_FILE_PREFERENCES.set(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuMask));
      HOTKEY_FILE_QUIT.set(KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuMask));
      HOTKEY_PROJ_ADD_CIRCUIT.set(KeyStroke.getKeyStroke(KeyEvent.VK_N,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_PROJ_ANALYZE.set(KeyStroke.getKeyStroke(KeyEvent.VK_T,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_PROJ_STATS.set(KeyStroke.getKeyStroke(KeyEvent.VK_I,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_PROJ_OPTIONS.set(KeyStroke.getKeyStroke(KeyEvent.VK_O,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_FILE_EXPORT.set(KeyStroke.getKeyStroke(KeyEvent.VK_E,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_FILE_PRINT.set(KeyStroke.getKeyStroke(KeyEvent.VK_P, menuMask));
      HOTKEY_SEARCH.set(KeyStroke.getKeyStroke(KeyEvent.VK_A,
          InputEvent.SHIFT_DOWN_MASK | menuMask));
      HOTKEY_TOOL_SELECT_1.set(KeyStroke.getKeyStroke(KeyEvent.VK_1, menuMask));
      HOTKEY_TOOL_SELECT_2.set(KeyStroke.getKeyStroke(KeyEvent.VK_2, menuMask));
      HOTKEY_TOOL_SELECT_3.set(KeyStroke.getKeyStroke(KeyEvent.VK_3, menuMask));
      HOTKEY_TOOL_SELECT_4.set(KeyStroke.getKeyStroke(KeyEvent.VK_4, menuMask));
      HOTKEY_TOOL_SELECT_5.set(KeyStroke.getKeyStroke(KeyEvent.VK_5, menuMask));
      HOTKEY_TOOL_SELECT_6.set(KeyStroke.getKeyStroke(KeyEvent.VK_6, menuMask));
      HOTKEY_TOOL_SELECT_7.set(KeyStroke.getKeyStroke(KeyEvent.VK_7, menuMask));
      HOTKEY_TOOL_SELECT_8.set(KeyStroke.getKeyStroke(KeyEvent.VK_8, menuMask));
      HOTKEY_TOOL_SELECT_9.set(KeyStroke.getKeyStroke(KeyEvent.VK_9, menuMask));
      HOTKEY_TOOL_SELECT_10.set(KeyStroke.getKeyStroke(KeyEvent.VK_0, menuMask));
      HOTKEY_TOOL_SELECT_11.set(null);
      HOTKEY_TOOL_SELECT_12.set(null);
      HOTKEY_TOOL_SELECT_13.set(null);
      HOTKEY_TOOL_SELECT_14.set(null);
      HOTKEY_PROJ_MOVE_UP.set(KeyStroke.getKeyStroke(
          KeyEvent.VK_U, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask));
      HOTKEY_PROJ_MOVE_DOWN.set(KeyStroke.getKeyStroke(
          KeyEvent.VK_D, InputEvent.SHIFT_DOWN_MASK | hotkeyMenuMask));
      HOTKEY_DIR_NORTH.set(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
      HOTKEY_DIR_SOUTH.set(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
      HOTKEY_DIR_EAST.set(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
      HOTKEY_DIR_WEST.set(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0));
      HOTKEY_EDIT_MENU_DUPLICATE.set(KeyStroke.getKeyStroke(KeyEvent.VK_D, hotkeyMenuMask));
      HOTKEY_EDIT_TOOL_DUPLICATE.set(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0));
      HOTKEY_AUTO_LABEL_OPEN.set(KeyStroke.getKeyStroke(KeyEvent.VK_L, 0));
      HOTKEY_AUTO_LABEL_TOGGLE.set(KeyStroke.getKeyStroke(KeyEvent.VK_T, 0));
      HOTKEY_AUTO_LABEL_VIEW.set(KeyStroke.getKeyStroke(KeyEvent.VK_V, 0));
      HOTKEY_AUTO_LABEL_HIDE.set(KeyStroke.getKeyStroke(KeyEvent.VK_H, 0));
      HOTKEY_ADD_TOOL_ROTATE.set(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0));
      ((PrefMonitorKeyStroke) HOTKEY_GATE_MODIFIER_SIZE_SMALL).set(new KeyStroke[] {
          KeyStroke.getKeyStroke(KeyEvent.VK_S, 0),
          KeyStroke.getKeyStroke(KeyEvent.VK_N, 0),
      });
      HOTKEY_GATE_MODIFIER_SIZE_MEDIUM.set(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0));
      HOTKEY_GATE_MODIFIER_SIZE_WIDE.set(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0));
      ((PrefMonitorKeyStroke) HOTKEY_GATE_MODIFIER_INPUT_ADD).set(new KeyStroke[] {
          KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0),
          KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0),
          KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0),
      });
      ((PrefMonitorKeyStroke) HOTKEY_GATE_MODIFIER_INPUT_SUB).set(new KeyStroke[] {
          KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0),
          KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0),
      });
      HOTKEY_AUTO_LABEL_SELF_NUMBERED_STOP.set(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0));
      AppPreferences.getPrefs().flush();
    } catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  public static final List<Menu> gui_sync_objects = new CopyOnWriteArrayList<>();

  private static final KeyStroke SWING_SHOW_TOOL_TIP_SHORTCUT =
      KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.CTRL_DOWN_MASK);

  public static void hotkeySync() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(AppPreferences::hotkeySync);
      return;
    }
    // Preference writes are already queued by the monitors; never block Swing on a disk flush.
    for (final var menu : gui_sync_objects) {
      menu.hotkeyUpdate();
    }
  }

  public static void hotkeyReflectError(Exception e) {
    e.printStackTrace();
  }

  public static String hotkeyCheckConflict(String keyName, int keyCode, int modifier) {
    if (SWING_SHOW_TOOL_TIP_SHORTCUT.equals(KeyStroke.getKeyStroke(keyCode, modifier))) {
      return S.get("hotkeyErrConflict", S.get("hotkeyReservedShowToolTip"));
    }
    try {
      /* Check the supported hotkey bindings */
      Field[] fields = AppPreferences.class.getDeclaredFields();

      for (var f : fields) {
        String name = f.getName();
        if (name.contains("HOTKEY_")) {
          @SuppressWarnings("unchecked")
          PrefMonitor<KeyStroke> keyStroke = (PrefMonitor<KeyStroke>) f.get(AppPreferences.class);
          if (((PrefMonitorKeyStroke) keyStroke).compare(keyCode, modifier)) {
            if (((PrefMonitorKeyStroke) keyStroke).getName().equals(keyName)) {
              return "";
            }
            return S.get("hotkeyErrConflict",
                S.get(((PrefMonitorKeyStroke) keyStroke).getName()));
          }
        }
      }

      /* Check all the menu items */
      for (var m : gui_sync_objects) {
        Field[] menuFields = m.getClass().getDeclaredFields();
        for (var f : menuFields) {
          f.setAccessible(true);
          KeyStroke itemStroke = null;
          String text = "";
          if (f.getType().toString().contains("com.cburch.logisim.gui.menu.MenuItemImpl")) {
            MenuItemImpl item = (MenuItemImpl) f.get(m);
            itemStroke = item.getAccelerator();
            text = item.getText();
          } else if (f.getType().toString().contains("javax.swing.JMenuItem")) {
            JMenuItem item = (JMenuItem) f.get(m);
            itemStroke = item.getAccelerator();
            text = item.getText();
          }
          if (itemStroke == null) {
            continue;
          }
          String compareString = InputEvent.getModifiersExText(itemStroke.getModifiers()) + "+"
              + KeyEvent.getKeyText(itemStroke.getKeyCode());
          String expectedKey = InputEvent.getModifiersExText(modifier) + "+"
              + KeyEvent.getKeyText(keyCode);
          if (expectedKey.equals(compareString)) {
            return S.get("hotkeyErrConflict", text);
          }
        }
      }
    } catch (Exception e) {
      hotkeyReflectError(e);
    }
    return "";
  }
}
