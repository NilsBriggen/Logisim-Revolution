/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.std.arith.ArithmeticLibrary;
import com.cburch.logisim.std.arith.floating.FPArithmeticLibrary;
import com.cburch.logisim.std.base.BaseLibrary;
import com.cburch.logisim.std.bfh.BfhLibrary;
import com.cburch.logisim.std.gates.GatesLibrary;
import com.cburch.logisim.std.hdl.HdlLibrary;
import com.cburch.logisim.std.io.IoLibrary;
import com.cburch.logisim.std.io.extra.ExtraIoLibrary;
import com.cburch.logisim.std.memory.MemoryLibrary;
import com.cburch.logisim.std.plexers.PlexersLibrary;
import com.cburch.logisim.std.tcl.TclLibrary;
import com.cburch.logisim.std.ttl.TtlLibrary;
import com.cburch.logisim.std.wiring.WiringLibrary;
import com.cburch.logisim.soc.Soc;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import java.util.Map;

/**
 * Resolves a {@link ComponentFactory} (or a {@link Library}) to the JavaHelp <em>mapID</em> target
 * that should be shown when the user asks for context help on it (F1, or the "Help for &lt;X&gt;"
 * menu items on the canvas and the parts picker). The target string is passed straight to {@link
 * MenuHelp#showHelp(String)}.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>an explicit per-factory override in {@link #FACTORY_TARGET}, keyed by the factory's stable
 *       {@code getName()} (i.e. its {@code _ID});
 *   <li>the owning library's index page, from {@link #LIBRARY_TARGET}, keyed by the library's
 *       {@code getName()} (its {@code _ID});
 *   <li>the top-level Library Reference page ({@code "libs"}), if neither of the above is known.
 * </ol>
 *
 * <p>Every target used here must exist as a {@code mapID} in {@code doc/map_en.jhm}; this is
 * enforced by a unit test that walks every factory of every library in {@link Builtin}.
 */
public final class ComponentHelp {

  /** Default target shown when nothing more specific is known. */
  public static final String LIBRARY_REFERENCE_TARGET = "libs";

  /**
   * Library {@code _ID} -&gt; help target for that library's own index/overview page. Used as the
   * fallback for any factory in the library that has no dedicated entry in {@link #FACTORY_TARGET}.
   *
   * <p>{@code BfhLibrary} and {@code HdlLibrary} have no dedicated Library Reference page at all, so
   * they intentionally fall back to the top-level {@link #LIBRARY_REFERENCE_TARGET}.
   */
  private static final Map<String, String> LIBRARY_TARGET =
      Map.ofEntries(
          Map.entry(BaseLibrary._ID, "base"),
          Map.entry(GatesLibrary._ID, "gates"),
          Map.entry(WiringLibrary._ID, "wiring"),
          Map.entry(PlexersLibrary._ID, "plexers"),
          Map.entry(ArithmeticLibrary._ID, "arith"),
          Map.entry(FPArithmeticLibrary._ID, "fparith"),
          Map.entry(MemoryLibrary._ID, "mem"),
          Map.entry(IoLibrary._ID, "io"),
          Map.entry(ExtraIoLibrary._ID, "ioex"),
          Map.entry(TtlLibrary._ID, "ttl"),
          Map.entry(TclLibrary._ID, "tcl"),
          Map.entry(Soc._ID, "soc"),
          Map.entry(BfhLibrary._ID, LIBRARY_REFERENCE_TARGET),
          Map.entry(HdlLibrary._ID, LIBRARY_REFERENCE_TARGET));

  /**
   * ComponentFactory {@code getName()} (its {@code _ID}) -&gt; specific help target. Only components
   * with either a dedicated page, or a page shared by a small documented category (e.g. all basic
   * gates share {@code gates_basic}), are listed here. Anything absent falls back to its library's
   * index page via {@link #LIBRARY_TARGET}.
   */
  private static final Map<String, String> FACTORY_TARGET =
      Map.ofEntries(
          // Gates
          Map.entry("NOT Gate", "gates_not"),
          Map.entry("Buffer", "gates_buffer"),
          Map.entry("AND Gate", "gates_basic"),
          Map.entry("OR Gate", "gates_basic"),
          Map.entry("NAND Gate", "gates_basic"),
          Map.entry("NOR Gate", "gates_basic"),
          Map.entry("XOR Gate", "gates_xor"),
          Map.entry("XNOR Gate", "gates_xor"),
          Map.entry("Odd Parity", "gates_xor"),
          Map.entry("Even Parity", "gates_xor"),
          Map.entry("Controlled Buffer", "gates_controlled"),
          Map.entry("Controlled Inverter", "gates_controlled"),
          Map.entry("PLA", "gates_PLA"),

          // Wiring
          Map.entry("Splitter", "wiring_splitter"),
          Map.entry("Pin", "wiring_pin"),
          Map.entry("Probe", "wiring_probe"),
          Map.entry("Tunnel", "wiring_tunnel"),
          Map.entry("Pull Resistor", "wiring_pull"),
          Map.entry("Clock", "wiring_clock"),
          Map.entry("POR", "wiring_por"),
          Map.entry("Constant", "wiring_constant"),
          Map.entry("Power", "wiring_const01"),
          Map.entry("Ground", "wiring_const01"),
          Map.entry("NoConnect", "wiring_notconnect"),
          Map.entry("Transistor", "wiring_transist"),
          Map.entry("Transmission Gate", "wiring_transmis"),
          Map.entry("Bit Extender", "wiring_extender"),

          // Plexers
          Map.entry("Multiplexer", "plexers_mux"),
          Map.entry("Demultiplexer", "plexers_demux"),
          Map.entry("Decoder", "plexers_decoder"),
          Map.entry("Priority Encoder", "plexers_priencod"),
          Map.entry("BitSelector", "plexers_selector"),

          // Arithmetic
          Map.entry("Adder", "arith_adder"),
          Map.entry("Subtractor", "arith_subtractor"),
          Map.entry("Multiplier", "arith_multiplier"),
          Map.entry("Divider", "arith_divider"),
          Map.entry("Negator", "arith_negator"),
          Map.entry("Comparator", "arith_comparator"),
          Map.entry("Shifter", "arith_shifter"),
          Map.entry("BitAdder", "arith_bitadder"),
          Map.entry("BitFinder", "arith_bitfinder"),
          // Absolute, Exponentiator, MinMax and SquareRoot have no dedicated Arithmetic library
          // page; they intentionally fall back to the library index (see ComponentHelpTest).

          // Floating-point arithmetic
          Map.entry("FPAdder", "fparith_adder"),
          Map.entry("FPSubtractor", "fparith_subtractor"),
          Map.entry("FPMultiplier", "fparith_multiplier"),
          Map.entry("FPDivider", "fparith_divider"),
          Map.entry("FPNegator", "fparith_negator"),
          Map.entry("FPExponentiator", "fparith_exponentiator"),
          Map.entry("FPLogarithm", "fparith_logarithm"),
          Map.entry("FPSquareRoot", "fparith_squareroot"),
          Map.entry("FPAbsolute", "fparith_absolute"),
          Map.entry("FPComparator", "fparith_comparator"),
          Map.entry("FPMinMax", "fparith_minmax"),
          Map.entry("FPRound", "fparith_round"),
          Map.entry("FPTrigonometry", "fparith_trigonometry"),
          Map.entry("FPClassificator", "fparith_classificator"),
          Map.entry("FPToFP", "fparith_fpconverter"),
          Map.entry("FPToInt", "fparith_fptoint"),
          Map.entry("IntToFP", "fparith_inttofp"),
          // No dedicated page for the floating-point constant; falls back to the library index.
          Map.entry("FPConstant", "fparith"),

          // Memory
          Map.entry("D Flip-Flop", "mem_flipflops"),
          Map.entry("T Flip-Flop", "mem_flipflops"),
          Map.entry("J-K Flip-Flop", "mem_flipflops"),
          Map.entry("S-R Flip-Flop", "mem_flipflops"),
          Map.entry("Register", "mem_register"),
          Map.entry("Counter", "mem_counter"),
          Map.entry("Shift Register", "mem_shiftreg"),
          Map.entry("Random", "mem_random"),
          Map.entry("RAM", "mem_ram"),
          Map.entry("DualRAM", "mem_ram"),
          Map.entry("ROM", "mem_rom"),

          // I/O
          Map.entry("Button", "io_button"),
          Map.entry("DipSwitch", "io_DIP"),
          Map.entry("Joystick", "io_joystick"),
          Map.entry("Keyboard", "io_keyboard"),
          Map.entry("LED", "io_led"),
          Map.entry("LedBar", "io_ledbar"),
          Map.entry("RGBLED", "io_ledRGB"),
          Map.entry("7-Segment Display", "io_7seg"),
          Map.entry("Hex Digit Display", "io_hexdig"),
          Map.entry("DotMatrix", "io_dotmat"),
          Map.entry("TTY", "io_tty"),
          Map.entry("Telnet", "io_telnet"),
          Map.entry("RealTimeClock", "io_realtimeclock"),
          Map.entry("MatrixKeypad", "io_matrixkeypad"),
          Map.entry("PortIO", "io_Port_IO"),
          Map.entry("ReptarLB", "io_Repetar"),
          Map.entry("RGB Video", "io_videorvb"),

          // Input/Output-Extra
          Map.entry("Switch", "ioex_switch"),
          Map.entry("Buzzer", "ioex_buzzer"),
          Map.entry("Slider", "ioex_slider"),
          Map.entry("Digital Oscilloscope", "ioex_digitalscope"),
          Map.entry("PlaRom", "ioex_plarom"),
          Map.entry("TwoWaySwitch", "ioex_twowayswitch"),
          Map.entry("TWOPINLED", "ioex_twopinled"),
          // No dedicated page; falls back to the library index.
          Map.entry("ProgrammableGenerator", "ioex"),

          // Base
          Map.entry("Text", "base_label"),
          // No dedicated page; falls back to the library index.
          Map.entry("Image", "base"),

          // TCL
          Map.entry("TclConsoleReds", "tcl_reds_console"),
          Map.entry("TclGeneric", "tcl_generic"),

          // SoC
          Map.entry("SocBus", "socbus"));

  private ComponentHelp() {}

  /**
   * Resolves the help target for a component factory, given the {@code _ID} of the library it
   * belongs to (as returned by {@link Library#getName()}). Falls back to that library's index page,
   * then to the top-level Library Reference, if no more specific target is known.
   */
  public static String getHelpTarget(ComponentFactory factory, String libraryId) {
    if (factory != null) {
      final var specific = FACTORY_TARGET.get(factory.getName());
      if (specific != null) return specific;
    }
    if (libraryId != null) {
      final var libTarget = LIBRARY_TARGET.get(libraryId);
      if (libTarget != null) return libTarget;
    }
    return LIBRARY_REFERENCE_TARGET;
  }

  /**
   * Resolves the help target for a component factory by locating the library that owns it within
   * {@code root} (typically the project's {@code LogisimFile}). Used at the UI layer, where only the
   * factory (not its owning library) is directly at hand.
   */
  public static String getHelpTarget(ComponentFactory factory, Library root) {
    final var owner = findOwningLibrary(root, factory);
    return getHelpTarget(factory, owner == null ? null : owner.getName());
  }

  /** Resolves the help target for a {@link Tool}, unwrapping an {@link AddTool} to its factory. */
  public static String getHelpTarget(Tool tool, Library root) {
    if (tool instanceof AddTool addTool) {
      return getHelpTarget(addTool.getFactory(), root);
    }
    return LIBRARY_REFERENCE_TARGET;
  }

  /** Resolves the help target for a whole library: its own index page, or the top-level one. */
  public static String getHelpTarget(Library library) {
    if (library == null) return LIBRARY_REFERENCE_TARGET;
    final var target = LIBRARY_TARGET.get(library.getName());
    return target != null ? target : LIBRARY_REFERENCE_TARGET;
  }

  /**
   * Finds the immediate {@link Library} within {@code root} (searched recursively, depth-first)
   * whose own tools include {@code factory}. Returns {@code null} if not found (e.g. a subcircuit
   * or a factory from a library that is not currently loaded).
   */
  private static Library findOwningLibrary(Library root, ComponentFactory factory) {
    if (root == null || factory == null) return null;
    if (root.contains(factory)) return root;
    for (final var sub : root.getLibraries()) {
      final var found = findOwningLibrary(sub, factory);
      if (found != null) return found;
    }
    return null;
  }
}
