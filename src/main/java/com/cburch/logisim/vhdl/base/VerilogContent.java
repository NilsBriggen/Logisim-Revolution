/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.base;

import static com.cburch.logisim.vhdl.Strings.S;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.fpga.designrulecheck.CorrectLabel;
import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Vhdl;
import com.cburch.logisim.gui.generic.OptionPane;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/**
 * The source of a Verilog module component: one {@code module ... endmodule} whose ANSI header
 * gives the component its ports.
 *
 * <p>It reuses the VHDL entity machinery (editor, project explorer, attribute table, undo, saving),
 * so every place that handles a {@link VhdlContent} handles a Verilog module too; {@link
 * #isVerilog()} tells them apart where the language matters. Only the header is checked: the body
 * is copied verbatim into the generated HDL, and is not simulated inside Logisim.
 */
public class VerilogContent extends VhdlContent {

  /** Language marker saved on the {@code <vhdl>} element of a Verilog module. */
  public static final String LANGUAGE = "verilog";

  static final Attribute<String> NAME_ATTR =
      Attributes.forString("verilogModule", S.getter("verilogModuleName"));

  private static final String RESOURCE = "/resources/logisim/hdl/verilog_module.templ";
  private static final String TEMPLATE = loadVerilogTemplate();

  private static String loadVerilogTemplate() {
    try (InputStream input = VerilogContent.class.getResourceAsStream(RESOURCE)) {
      if (input == null) return "module %modulename%();\nendmodule\n";
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      return "module %modulename%();\nendmodule\n";
    }
  }

  /** A new module named {@code name}, filled from the template. */
  public static VerilogContent create(String name, LogisimFile file) {
    final var content = new VerilogContent(name, file);
    if (!content.setContent(TEMPLATE.replace("%modulename%", name))) content.showErrors();
    return content;
  }

  /** A module read from {@code verilog}; reports problems to the user, like the VHDL entities. */
  public static VerilogContent parse(String name, String verilog, LogisimFile file) {
    final var content = new VerilogContent(name, file);
    if (!content.setContent(verilog)) content.showErrors();
    return content;
  }

  /** Why {@code label} cannot name a Verilog module in {@code file}, or null when it can. */
  public static String nameProblem(String label, LogisimFile file) {
    // The same identifier rules as circuits and VHDL entities: the name ends up in generated HDL.
    if (label == null
        || !label.matches("^[A-Za-z]\\w*")
        || label.endsWith("_")
        || label.contains("__")) {
      return S.get("verilogInvalidNameError");
    }
    if (Vhdl.VHDL_KEYWORDS.contains(label.toLowerCase(Locale.ROOT))
        || CorrectLabel.isKeyword(label, HdlGeneratorFactory.VERILOG, false)) {
      return S.get("verilogKeywordNameError");
    }
    if (file != null && file.containsFactory(label)) return S.get("verilogDuplicateNameError");
    return null;
  }

  /** Shows why {@code label} is refused and returns true, or returns false when it is valid. */
  public static boolean labelVerilogInvalidNotify(String label, LogisimFile file) {
    final var problem = nameProblem(label, file);
    if (problem == null) return false;
    OptionPane.showMessageDialog(
        null, label + ": " + problem, S.get("verilogParseError"), OptionPane.ERROR_MESSAGE);
    return true;
  }

  private int nameStart = -1;
  private int nameEnd = -1;

  protected VerilogContent(String name, LogisimFile file) {
    super(name, file);
  }

  @Override
  public VerilogContent clone() {
    return (VerilogContent) super.clone();
  }

  @Override
  public boolean isVerilog() {
    return true;
  }

  @Override
  public Attribute<String> getNameAttribute() {
    return NAME_ATTR;
  }

  @Override
  public VhdlEntity createFactory() {
    return new VerilogModule(this);
  }

  /**
   * The module source for HDL generation, with the module renamed to {@code moduleName} (the
   * generated designs instantiate every component under its HDL name). The rest is verbatim.
   */
  public String getSourceNamed(String moduleName) {
    final var source = getContent();
    if (!valid || nameStart < 0 || nameEnd > source.length()) return source;
    return source.substring(0, nameStart) + moduleName + source.substring(nameEnd);
  }

  @Override
  public boolean setName(String newName) {
    if (newName == null || labelVerilogInvalidNotify(newName, logiFile)) return false;
    if (!valid || nameStart < 0) return false;
    final var source = getContent();
    return setContent(source.substring(0, nameStart) + newName + source.substring(nameEnd));
  }

  @Override
  public boolean setContent(String verilog) {
    if (setContentNoValidation(verilog)) return true;

    try {
      errTitle.setLength(0);
      errMessage.setLength(0);
      errCode = 0;
      final var parser = new VerilogParser(content.toString());
      try {
        parser.parse();
      } catch (VerilogParser.IllegalVerilogContentException ex) {
        errTitle.append(S.get("verilogParseError"));
        errMessage.append(ex.getMessage());
        return false;
      }
      final var newName = parser.getName();
      final var problem = nameProblem(newName, newName.equals(name) ? null : logiFile);
      if (problem != null) {
        errTitle.append(S.get("verilogParseError"));
        errMessage.append(newName).append(": ").append(problem);
        return false;
      }

      name = newName;
      nameStart = parser.getNameStart();
      nameEnd = parser.getNameEnd();
      libraries = "";
      architecture = "";
      ports.clear();
      ports.addAll(parser.getPorts());
      generics = new Generic[0];
      genericAttrs = new ArrayList<>();
      staticAttrs = VhdlEntityAttributes.createBaseAttrs(this);
      valid = true;
      return true;
    } finally {
      fireContentSet();
    }
  }
}
