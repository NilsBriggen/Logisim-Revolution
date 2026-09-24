/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.icons;

import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;

/**
 * Semantic, theme-aware miniatures for known built-in factories, independent of factory loading.
 *
 * <p>Only UI thumbnails use these symbols. Gate and pin painters retain their attribute-dependent
 * shapes. Unknown factories, including third-party subclasses, keep their own icon/paint fallback.
 */
public final class ComponentIcons {
  private enum Shape {
    CLOCK, RESET, PROBE, TUNNEL, CONSTANT, POWER, GROUND, DISCONNECTED, TRANSISTOR,
    TRANSMISSION, EXTENDER, MUX, DEMUX, DECODER, ENCODER, SELECTOR, PLA, ARITHMETIC,
    FLIP_FLOP, REGISTER, COUNTER, SHIFT, RANDOM, RAM, ROM, DUAL_RAM, BUTTON, SWITCH,
    JOYSTICK, KEYBOARD, LED, LED_BAR, RGB_LED, SEGMENT, HEX, MATRIX, TERMINAL, PORT,
    BUS, NETWORK, RTC, KEYPAD, VIDEO, CPU, DMA, MODULE, TTL, SOUND, SLIDER, SWITCH2, SCOPE
  }

  private static final Map<String, Icon> ICONS = createIcons();
  private static final Icon MODULE = new Symbol(Shape.MODULE, "", false);

  private ComponentIcons() {}

  /** Returns null for an unregistered factory, without initializing or constructing that factory. */
  public static Icon forFactory(Class<? extends ComponentFactory> factory) {
    if (factory.getClassLoader() != ComponentIcons.class.getClassLoader()) return null;
    return ICONS.get(factory.getName());
  }

  /** Generic module fallback; it never replaces an icon explicitly supplied by an extension. */
  public static Icon module() {
    return MODULE;
  }

  private static Map<String, Icon> createIcons() {
    final var icons = new HashMap<String, Icon>();
    add(icons, "wiring", "Clock", Shape.CLOCK);
    add(icons, "wiring", "PowerOnReset", Shape.RESET);
    add(icons, "wiring", "Probe", Shape.PROBE);
    add(icons, "wiring", "Tunnel", Shape.TUNNEL);
    add(icons, "wiring", "Constant", Shape.CONSTANT);
    add(icons, "wiring", "Power", Shape.POWER);
    add(icons, "wiring", "Ground", Shape.GROUND);
    add(icons, "wiring", "DoNotConnect", Shape.DISCONNECTED);
    add(icons, "wiring", "Transistor", Shape.TRANSISTOR);
    add(icons, "wiring", "TransmissionGate", Shape.TRANSMISSION);
    add(icons, "wiring", "BitExtender", Shape.EXTENDER);
    add(icons, "gates", "Pla", Shape.PLA);
    add(icons, "plexers", "Multiplexer", Shape.MUX);
    add(icons, "plexers", "Demultiplexer", Shape.DEMUX);
    add(icons, "plexers", "Decoder", Shape.DECODER);
    add(icons, "plexers", "PriorityEncoder", Shape.ENCODER);
    add(icons, "plexers", "BitSelector", Shape.SELECTOR);
    final String[][] operations = {
      {"Adder", "+"}, {"Subtractor", "−"}, {"Multiplier", "×"}, {"Divider", "÷"},
      {"Negator", "±"}, {"Exponentiator", "xⁿ"}, {"SquareRoot", "√"}, {"Absolute", "|x|"},
      {"Comparator", "≷"}, {"MinMax", "↕"}, {"Shifter", "≪"}, {"BitAdder", "Σ"},
      {"BitFinder", "?"}
    };
    for (final var operation : operations) {
      put(icons, "arith", operation[0], Shape.ARITHMETIC, operation[1], false);
      if (!operation[0].equals("Shifter") && !operation[0].equals("BitAdder")
          && !operation[0].equals("BitFinder")) {
        put(icons, "arith.floating", "Fp" + operation[0],
            Shape.ARITHMETIC, operation[1], true);
      }
    }
    put(icons, "arith.floating", "FpConstant", Shape.CONSTANT, "", true);
    put(icons, "arith.floating", "FpLogarithm", Shape.ARITHMETIC, "ln", true);
    put(icons, "arith.floating", "FpRound", Shape.ARITHMETIC, "≈", true);
    put(icons, "arith.floating", "FpTrigonometry", Shape.ARITHMETIC, "sin", true);
    put(icons, "arith.floating", "FpClassificator", Shape.ARITHMETIC, "?", true);
    put(icons, "arith.floating", "FpToFp", Shape.ARITHMETIC, "f↔f", true);
    put(icons, "arith.floating", "FpToInt", Shape.ARITHMETIC, "f→i", true);
    put(icons, "arith.floating", "IntToFp", Shape.ARITHMETIC, "i→f", true);
    for (final var type : new String[] {"D", "T", "JK", "SR"}) {
      put(icons, "memory", type + "FlipFlop", Shape.FLIP_FLOP, type, false);
    }
    add(icons, "memory", "Register", Shape.REGISTER);
    add(icons, "memory", "Counter", Shape.COUNTER);
    add(icons, "memory", "ShiftRegister", Shape.SHIFT);
    add(icons, "memory", "Random", Shape.RANDOM);
    add(icons, "memory", "Ram", Shape.RAM);
    add(icons, "memory", "Rom", Shape.ROM);
    add(icons, "memory", "DualRam", Shape.DUAL_RAM);
    final String[][] inputs = {
      {"Button", "BUTTON"}, {"DipSwitch", "SWITCH"}, {"Joystick", "JOYSTICK"},
      {"Keyboard", "KEYBOARD"}, {"Led", "LED"}, {"LedBar", "LED_BAR"},
      {"RgbLed", "RGB_LED"}, {"SevenSegment", "SEGMENT"}, {"HexDigit", "HEX"},
      {"DotMatrix", "MATRIX"}, {"Tty", "TERMINAL"}, {"PortIo", "PORT"},
      {"ReptarLocalBus", "BUS"}, {"Telnet", "NETWORK"}, {"RealTimeClock", "RTC"},
      {"MatrixKeypad", "KEYPAD"}, {"Video$Factory", "VIDEO"}
    };
    for (final var entry : inputs) add(icons, "io", entry[0], Shape.valueOf(entry[1]));
    add(icons, "io.extra", "Switch", Shape.SWITCH);
    add(icons, "io.extra", "Buzzer", Shape.SOUND);
    add(icons, "io.extra", "Slider", Shape.SLIDER);
    add(icons, "io.extra", "DigitalOscilloscope", Shape.SCOPE);
    add(icons, "io.extra", "PlaRom", Shape.PLA);
    add(icons, "io.extra", "TwoWaySwitch", Shape.SWITCH2);
    add(icons, "io.extra", "TwoPinLed", Shape.LED);
    put(icons, "bfh", "BinToBcd", Shape.ARITHMETIC, "BCD", false);
    add(icons, "bfh", "BcdToSevenSegmentDisplay", Shape.SEGMENT);
    put(icons, "tcl", "TclConsoleReds", Shape.MODULE, "Tcl", false);
    put(icons, "tcl", "TclGeneric", Shape.MODULE, "Tcl", false);
    put(icons, "hdl", "VhdlEntityComponent", Shape.MODULE, "HDL", false);
    put(icons, "hdl", "BlifCircuitComponent", Shape.MODULE, "BLIF", false);
    icons.put("com.cburch.logisim.soc.rv32im.Rv32imRiscV", new Symbol(Shape.CPU, "RV", false));
    icons.put("com.cburch.logisim.soc.nios2.Nios2", new Symbol(Shape.CPU, "N2", false));
    icons.put("com.cburch.logisim.soc.bus.SocBus", new Symbol(Shape.BUS, "", false));
    icons.put("com.cburch.logisim.soc.memory.SocMemory", new Symbol(Shape.RAM, "", false));
    icons.put("com.cburch.logisim.soc.pio.SocPio", new Symbol(Shape.PORT, "", false));
    icons.put("com.cburch.logisim.soc.vga.SocVga", new Symbol(Shape.VIDEO, "", false));
    icons.put("com.cburch.logisim.soc.dma.SocDma", new Symbol(Shape.DMA, "", false));
    icons.put("com.cburch.logisim.soc.jtaguart.JtagUart", new Symbol(Shape.TERMINAL, "", false));
    icons.put("com.cburch.logisim.circuit.SubcircuitFactory",
        new Symbol(Shape.MODULE, "", false));
    icons.put("com.cburch.logisim.vhdl.base.VhdlEntity", new Symbol(Shape.MODULE, "HDL", false));
    // Explicit built-in part names: a plug-in in a similarly named package must not be remapped.
    for (final var part : TTL_PARTS.split(" ")) {
      put(icons, "ttl", "Ttl" + part, Shape.TTL, "", false);
    }
    return Map.copyOf(icons);
  }

  private static void add(Map<String, Icon> icons, String group, String name, Shape shape) {
    put(icons, group, name, shape, "", false);
  }

  private static void put(
      Map<String, Icon> icons, String group, String name, Shape shape, String label, boolean fp) {
    icons.put("com.cburch.logisim.std." + group + "." + name, new Symbol(shape, label, fp));
  }

  private record Symbol(Shape shape, String label, boolean floating) implements Icon {
    @Override
    public int getIconWidth() {
      return AppPreferences.getIconSize();
    }

    @Override
    public int getIconHeight() {
      return getIconWidth();
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      final var g = (Graphics2D) graphics.create();
      try {
        g.translate(x, y);
        g.scale(getIconWidth() / 24.0, getIconHeight() / 24.0);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(AppPreferences.COMPONENT_ICON_COLOR.get()));
        final var symbolTransform = g.getTransform();
        if (floating) g.scale(0.82, 0.82);
        paintSymbol(g);
        if (floating) {
          // Reserve space for a legible f rather than writing it across the body outline.
          g.setTransform(symbolTransform);
          text(g, "f", 20, 20, 10);
        }
      } finally {
        g.dispose();
      }
    }

    private void paintSymbol(Graphics2D g) {
      switch (shape) {
        case CLOCK -> {
          rect(g, 2, 3, 20, 18);
          line(g, 4, 15, 7, 15, 7, 8, 12, 8, 12, 15, 17, 15, 17, 8, 20, 8);
        }
        case RESET -> {
          rect(g, 2, 3, 20, 18);
          line(g, 4, 15, 7, 15, 7, 8, 12, 8, 12, 15, 20, 15);
        }
        case CONSTANT -> {
          rect(g, 3, 4, 17, 16);
          text(g, "1", 11, 12, 13);
          line(g, 20, 12, 23, 12);
        }
        case PROBE -> {
          line(g, 1, 12, 6, 12);
          line(g, 6, 6, 19, 6, 23, 12, 19, 18, 6, 18, 6, 6);
          text(g, "01", 14, 12, 8);
        }
        case TUNNEL -> {
          line(g, 1, 12, 7, 12);
          line(g, 7, 12, 12, 6, 21, 6, 21, 18, 12, 18, 7, 12);
        }
        case POWER -> line(g, 12, 22, 12, 3, 5, 10, 12, 3, 19, 10);
        case GROUND -> {
          line(g, 12, 2, 12, 12);
          line(g, 3, 12, 21, 12);
          line(g, 6, 16, 18, 16);
          line(g, 9, 20, 15, 20);
        }
        case DISCONNECTED -> {
          line(g, 2, 12, 12, 12);
          line(g, 8, 7, 18, 17);
          line(g, 8, 17, 18, 7);
        }
        case TRANSISTOR -> {
          line(g, 2, 12, 8, 12);
          line(g, 8, 5, 8, 19);
          line(g, 11, 5, 11, 19);
          line(g, 11, 8, 18, 8, 18, 2);
          line(g, 11, 16, 18, 16, 18, 22);
        }
        case TRANSMISSION -> {
          line(g, 1, 12, 7, 12);
          line(g, 7, 5, 17, 19, 17, 5, 7, 19, 7, 5);
          line(g, 17, 12, 23, 12);
          line(g, 12, 2, 12, 7);
          line(g, 12, 17, 12, 22);
        }
        case EXTENDER -> {
          line(g, 1, 12, 7, 12, 7, 4, 17, 4, 17, 20, 7, 20, 7, 12);
          for (var y = 6; y <= 18; y += 6) line(g, 17, y, 23, y);
          text(g, "↗", 12, 12, 9);
        }
        case MUX, DEMUX -> {
          if (shape == Shape.DEMUX) {
            g.translate(24, 0);
            g.scale(-1, 1);
          }
          line(g, 6, 3, 18, 7, 18, 17, 6, 21, 6, 3);
          line(g, 1, 7, 6, 7);
          line(g, 1, 17, 6, 17);
          line(g, 18, 12, 23, 12);
          line(g, 12, 19, 12, 23);
        }
        case DECODER, ENCODER, SELECTOR -> {
          block(g);
          text(g, shape == Shape.DECODER ? "↓" : shape == Shape.ENCODER ? "↑" : "[]",
              12, 12, 11);
        }
        case PLA -> {
          block(g);
          for (var n = 8; n <= 16; n += 4) {
            line(g, 7, n, 17, n);
            line(g, n, 7, n, 17);
          }
          dot(g, 8, 12, 1.5);
          dot(g, 16, 8, 1.5);
        }
        case ARITHMETIC -> {
          block(g);
          text(g, label, 12, 12, label.length() > 2 ? 7 : 12);
        }
        case FLIP_FLOP -> {
          block(g);
          line(g, 4, 15, 7, 17, 4, 19);
          text(g, label, 12, 10, 10);
        }
        case REGISTER, COUNTER, SHIFT, RANDOM -> {
          block(g);
          final var symbol = switch (shape) {
            case REGISTER -> "01";
            case COUNTER -> "+1";
            case SHIFT -> "→";
            default -> "?";
          };
          text(g, symbol, 12, 12, 10);
        }
        case RAM, ROM, DUAL_RAM -> {
          block(g);
          for (var y = 8; y <= 16; y += 4) line(g, 7, y, 17, y);
          if (shape == Shape.ROM) {
            line(g, 9, 5, 9, 2, 15, 2, 15, 5);
          } else if (shape == Shape.DUAL_RAM) {
            line(g, 12, 6, 12, 18);
            line(g, 8, 2, 8, 4);
            line(g, 16, 20, 16, 22);
          } else {
            line(g, 10, 2, 14, 2);
            line(g, 10, 22, 14, 22);
          }
        }
        case BUTTON -> {
          rect(g, 3, 8, 18, 12);
          rect(g, 7, 3, 10, 5);
          line(g, 8, 20, 8, 23);
          line(g, 16, 20, 16, 23);
        }
        case SWITCH -> {
          rect(g, 3, 3, 18, 18);
          for (var x = 7; x <= 17; x += 5) {
            line(g, x, 6, x, 18);
            rect(g, x - 1.5, x == 12 ? 13 : 7, 3, 4);
          }
        }
        case SWITCH2 -> {
          dot(g, 4, 12, 2);
          dot(g, 20, 5, 2);
          dot(g, 20, 19, 2);
          line(g, 6, 11, 17, 6);
        }
        case SOUND -> {
          line(g, 2, 9, 6, 9, 11, 4, 11, 20, 6, 15, 2, 15, 2, 9);
          g.draw(new java.awt.geom.Arc2D.Double(10, 6, 8, 12, -60, 120,
              java.awt.geom.Arc2D.OPEN));
          g.draw(new java.awt.geom.Arc2D.Double(10, 2, 12, 20, -60, 120,
              java.awt.geom.Arc2D.OPEN));
        }
        case SLIDER -> {
          line(g, 3, 12, 21, 12);
          line(g, 3, 8, 3, 16);
          line(g, 21, 8, 21, 16);
          rect(g, 8, 5, 5, 14);
        }
        case SCOPE -> {
          rect(g, 2, 3, 20, 18);
          line(g, 4, 15, 8, 15, 10, 7, 13, 17, 16, 10, 20, 10);
        }
        case JOYSTICK -> {
          g.draw(new Ellipse2D.Double(3, 13, 18, 8));
          line(g, 12, 16, 15, 7);
          g.draw(new Ellipse2D.Double(12, 2, 6, 6));
        }
        case KEYBOARD, KEYPAD -> {
          rect(g, 2, shape == Shape.KEYBOARD ? 6 : 2, 20,
              shape == Shape.KEYBOARD ? 14 : 20);
          for (var y = 9; y <= 15; y += 6) {
            for (var x = 6; x <= 18; x += 6) dot(g, x, y, 1);
          }
          if (shape == Shape.KEYBOARD) line(g, 8, 18, 16, 18);
          else for (var x = 6; x <= 18; x += 6) dot(g, x, 4, 1);
        }
        case LED, RGB_LED -> {
          g.draw(new Ellipse2D.Double(5, 3, 14, 14));
          line(g, 8, 17, 8, 22);
          line(g, 16, 17, 16, 22);
          if (shape == Shape.RGB_LED) {
            line(g, 12, 4, 12, 10, 6, 13);
            line(g, 12, 10, 18, 13);
          } else dot(g, 12, 10, 2);
        }
        case LED_BAR -> {
          rect(g, 2, 5, 20, 14);
          for (var x = 6; x <= 18; x += 4) line(g, x, 8, x, 16);
        }
        case SEGMENT, HEX -> {
          rect(g, 4, 2, 16, 20);
          text(g, shape == Shape.HEX ? "A" : "8", 12, 12, 17);
        }
        case MATRIX -> {
          rect(g, 2, 2, 20, 20);
          for (var x = 6; x <= 18; x += 6) {
            for (var y = 6; y <= 18; y += 6) dot(g, x, y, 1);
          }
        }
        case TERMINAL, NETWORK -> {
          rect(g, 2, 3, 20, 17);
          line(g, 5, 7, 9, 10, 5, 13);
          line(g, 12, 14, 17, 14);
          if (shape == Shape.NETWORK) {
            line(g, 12, 20, 12, 23, 21, 23);
          }
        }
        case PORT, BUS, DMA -> {
          if (shape == Shape.BUS) {
            for (var y = 6; y <= 18; y += 6) line(g, 2, y, 22, y);
            line(g, 6, 6, 6, 18);
            line(g, 18, 6, 18, 18);
          } else {
            block(g);
            text(g, shape == Shape.DMA ? "⇄" : "↔", 12, 12, 12);
          }
        }
        case RTC -> {
          g.draw(new Ellipse2D.Double(2, 2, 20, 20));
          line(g, 12, 5, 12, 12, 17, 14);
        }
        case VIDEO -> {
          rect(g, 2, 2, 20, 16);
          line(g, 12, 18, 12, 22);
          line(g, 7, 22, 17, 22);
          line(g, 6, 13, 10, 8, 15, 13, 18, 9);
        }
        case CPU, MODULE, TTL -> {
          block(g);
          if (shape == Shape.TTL) {
            line(g, 9, 4, 9, 6, 15, 6, 15, 4);
            line(g, 8, 12, 16, 12);
          } else if (!label.isEmpty()) text(g, label, 12, 12, 8);
          else {
            rect(g, 8, 8, 8, 8);
            line(g, 12, 4, 12, 8);
            line(g, 12, 16, 12, 20);
          }
        }
      }
    }
  }

  private static void block(Graphics2D g) {
    rect(g, 4, 4, 16, 16);
    for (var y = 8; y <= 16; y += 8) {
      line(g, 1, y, 4, y);
      line(g, 20, y, 23, y);
    }
  }

  private static void rect(Graphics2D g, double x, double y, double width, double height) {
    g.draw(new Rectangle2D.Double(x, y, width, height));
  }

  private static void dot(Graphics2D g, double x, double y, double radius) {
    g.fill(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
  }

  private static void line(Graphics2D g, double... points) {
    final var path = new Path2D.Double();
    path.moveTo(points[0], points[1]);
    for (var i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
    g.draw(path);
  }

  private static void text(Graphics2D g, String value, float x, float y, float size) {
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(size));
    final var metrics = g.getFontMetrics();
    g.drawString(value, x - metrics.stringWidth(value) / 2f,
        y + (metrics.getAscent() - metrics.getDescent()) / 2f);
  }

  private static final String TTL_PARTS =
      "7400 7402 7404 7408 7410 7411 7413 7414 7418 7419 7420 7421 "
      + "7424 7427 7430 7432 7434 7436 7438 7442 7443 7444 7447 7451 "
      + "7454 7458 7464 7474 7476 7485 7486 7487 7493 74125 74138 74139 "
      + "74151 74153 74157 74158 74161 74163 74164 74165 74166 74173 74175 74181 "
      + "74182 74192 74193 74194 74240 74241 74244 74245 74266 74273 74283 74299 "
      + "74377 74381 74541 74670 747266";
}
