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

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.instance.Port;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the header of a single Verilog module: its name and its ANSI-style port list.
 *
 * <p>Only what Logisim needs to draw and connect the component is understood: {@code input},
 * {@code output} and {@code inout} ports, optionally typed {@code wire}/{@code reg}/{@code logic}
 * and {@code signed}, with an optional constant {@code [msb:lsb]} range. The module body is not
 * interpreted; it is only scanned for the closing {@code endmodule}. Everything else (parameter
 * port lists, non-ANSI port declarations, parameterised ranges, several modules) is refused with a
 * message naming the line, because Logisim could not derive fixed ports from it.
 */
public class VerilogParser {

  /** A Verilog module header Logisim cannot use, with the one-based line it concerns. */
  public static class IllegalVerilogContentException extends Exception {
    private static final long serialVersionUID = 1L;
    private final int line;

    public IllegalVerilogContentException(int line, String message) {
      super(S.get("verilogErrorAtLine", line, message));
      this.line = line;
    }

    /** One-based line in the source; 0 when the problem is not tied to a line. */
    public int getLine() {
      return line;
    }
  }

  private enum Kind {
    IDENT,
    NUMBER,
    SYMBOL,
    STRING,
    DIRECTIVE,
    EOF
  }

  private record Token(Kind kind, String text, int start, int end, int line) {
    boolean is(String value) {
      return (kind == Kind.IDENT || kind == Kind.SYMBOL) && text.equals(value);
    }
  }

  private static final Set<String> DIRECTIONS = Set.of("input", "output", "inout");
  private static final Set<String> NET_TYPES = Set.of("wire", "reg", "logic", "tri");
  private static final Set<String> SIGNING = Set.of("signed", "unsigned");

  private final String source;
  private int pos;
  private int line;
  private Token peeked;

  private String name;
  private int nameStart = -1;
  private int nameEnd = -1;
  private final List<VhdlParser.PortDescription> ports = new ArrayList<>();

  public VerilogParser(String source) {
    this.source = source == null ? "" : source;
  }

  /** The module name, as written. */
  public String getName() {
    return name;
  }

  /** The ports in declaration order. */
  public List<VhdlParser.PortDescription> getPorts() {
    return ports;
  }

  /** Offset of the module name in the source, so it can be renamed in place. */
  public int getNameStart() {
    return nameStart;
  }

  /** Offset just past the module name in the source. */
  public int getNameEnd() {
    return nameEnd;
  }

  public void parse() throws IllegalVerilogContentException {
    pos = 0;
    line = 1;
    peeked = null;
    ports.clear();
    if (source.isBlank()) throw new IllegalVerilogContentException(1, S.get("verilogEmptyError"));

    var tok = next();
    while (tok.kind == Kind.DIRECTIVE) tok = next();
    if (tok.kind == Kind.EOF || !(tok.is("module") || tok.is("macromodule"))) {
      throw error(tok, S.get("verilogNoModuleError"));
    }
    final var nameTok = next();
    if (nameTok.kind != Kind.IDENT || isKeyword(nameTok.text)) {
      throw error(nameTok, S.get("verilogModuleNameError"));
    }
    if (!isSimpleIdentifier(nameTok.text)) {
      throw error(nameTok, S.get("verilogIdentifierError", nameTok.text));
    }
    name = nameTok.text;
    nameStart = nameTok.start;
    nameEnd = nameTok.end;

    tok = next();
    if (tok.is("#")) throw error(tok, S.get("verilogParameterListError"));
    if (tok.is("(")) {
      parsePortList();
      tok = next();
    }
    if (!tok.is(";")) throw error(tok, S.get("verilogHeaderEndError", describe(tok)));

    parseBody();
  }

  private void parsePortList() throws IllegalVerilogContentException {
    var tok = peek();
    if (tok.is(")")) {
      next();
      return;
    }
    final Set<String> seen = new HashSet<>();
    String direction = null;
    var width = 1;
    while (true) {
      tok = next();
      if (tok.kind == Kind.IDENT && DIRECTIONS.contains(tok.text)) {
        direction = tok.text;
        width = 1;
        tok = next();
        if (tok.kind == Kind.IDENT && NET_TYPES.contains(tok.text)) tok = next();
        if (tok.kind == Kind.IDENT && SIGNING.contains(tok.text)) tok = next();
        if (tok.is("[")) {
          width = parseRange(tok);
          tok = next();
        }
      } else if (direction == null) {
        if (tok.kind == Kind.IDENT && !isKeyword(tok.text)) {
          throw error(tok, S.get("verilogNonAnsiError", tok.text));
        }
        throw error(tok, S.get("verilogPortExpectedError", describe(tok)));
      }
      if (tok.kind != Kind.IDENT || isKeyword(tok.text)) {
        throw error(tok, S.get("verilogPortTypeError", describe(tok)));
      }
      final var portName = tok.text;
      if (!isSimpleIdentifier(portName)) {
        throw error(tok, S.get("verilogIdentifierError", portName));
      }
      if (!seen.add(portName)) throw error(tok, S.get("verilogDuplicatePortError", portName));
      if (width > BitWidth.MAXWIDTH) {
        throw error(tok, S.get("verilogPortWidthError", portName, width, BitWidth.MAXWIDTH));
      }
      final var type =
          switch (direction) {
            case "input" -> Port.INPUT;
            case "output" -> Port.OUTPUT;
            default -> Port.INOUT;
          };
      ports.add(new VhdlParser.PortDescription(portName, type, width));

      tok = next();
      if (tok.is("[")) throw error(tok, S.get("verilogArrayPortError", portName));
      if (tok.is("=")) throw error(tok, S.get("verilogPortDefaultError", portName));
      if (tok.is(")")) return;
      if (!tok.is(",")) throw error(tok, S.get("verilogPortSeparatorError", describe(tok)));
    }
  }

  /** Reads {@code [msb:lsb]} after its opening bracket and returns the width. */
  private int parseRange(Token open) throws IllegalVerilogContentException {
    final var msb = next();
    final var colon = next();
    final var lsb = next();
    final var close = next();
    if (msb.kind != Kind.NUMBER
        || !colon.is(":")
        || lsb.kind != Kind.NUMBER
        || !close.is("]")
        || !isDecimal(msb.text)
        || !isDecimal(lsb.text)) {
      throw error(open, S.get("verilogRangeError"));
    }
    try {
      final var high = Long.parseLong(msb.text.replace("_", ""));
      final var low = Long.parseLong(lsb.text.replace("_", ""));
      final var width = Math.abs(high - low) + 1;
      return width > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) width;
    } catch (NumberFormatException e) {
      throw error(open, S.get("verilogRangeError"));
    }
  }

  private void parseBody() throws IllegalVerilogContentException {
    Token tok;
    while (true) {
      tok = next();
      if (tok.kind == Kind.EOF) throw error(tok, S.get("verilogNoEndmoduleError", name));
      if (tok.is("module") || tok.is("macromodule")) {
        throw error(tok, S.get("verilogNestedModuleError"));
      }
      if (tok.is("endmodule")) break;
    }
    while (true) {
      tok = next();
      if (tok.kind == Kind.EOF) return;
      if (tok.kind == Kind.DIRECTIVE) continue;
      if (tok.is("module") || tok.is("macromodule")) {
        throw error(tok, S.get("verilogSecondModuleError"));
      }
      throw error(tok, S.get("verilogTrailingTextError", describe(tok)));
    }
  }

  /** Escaped identifiers and {@code $} cannot be carried over into port maps and pin labels. */
  private static boolean isSimpleIdentifier(String text) {
    return text.matches("[A-Za-z_][A-Za-z0-9_]*");
  }

  private static boolean isDecimal(String text) {
    return text.matches("[0-9][0-9_]*");
  }

  private static boolean isKeyword(String text) {
    return DIRECTIONS.contains(text)
        || NET_TYPES.contains(text)
        || SIGNING.contains(text)
        || Set.of("module", "macromodule", "endmodule", "parameter", "localparam", "integer")
            .contains(text);
  }

  private static String describe(Token tok) {
    return tok.kind == Kind.EOF ? S.get("verilogEndOfText") : "'" + tok.text + "'";
  }

  private IllegalVerilogContentException error(Token tok, String message) {
    return new IllegalVerilogContentException(tok.line, message);
  }

  private Token peek() throws IllegalVerilogContentException {
    if (peeked == null) peeked = scan();
    return peeked;
  }

  private Token next() throws IllegalVerilogContentException {
    final var tok = peek();
    peeked = null;
    return tok;
  }

  /** Returns the next token, skipping white space and comments while counting lines. */
  private Token scan() throws IllegalVerilogContentException {
    while (pos < source.length()) {
      final var c = source.charAt(pos);
      if (c == '\n') {
        line++;
        pos++;
      } else if (Character.isWhitespace(c)) {
        pos++;
      } else if (source.startsWith("//", pos)) {
        while (pos < source.length() && source.charAt(pos) != '\n') pos++;
      } else if (source.startsWith("/*", pos)) {
        final var startLine = line;
        final var end = source.indexOf("*/", pos + 2);
        if (end < 0) {
          throw new IllegalVerilogContentException(startLine, S.get("verilogOpenCommentError"));
        }
        for (var i = pos; i < end; i++) {
          if (source.charAt(i) == '\n') line++;
        }
        pos = end + 2;
      } else {
        break;
      }
    }
    final var start = pos;
    final var startLine = line;
    if (pos >= source.length()) return new Token(Kind.EOF, "", start, start, startLine);
    final var c = source.charAt(pos);
    if (c == '`') {
      // Compiler directive: it applies to the text, not to the ports; skip to the end of the line.
      while (pos < source.length() && source.charAt(pos) != '\n') pos++;
      return new Token(Kind.DIRECTIVE, source.substring(start, pos), start, pos, startLine);
    }
    if (c == '"') {
      pos++;
      while (pos < source.length() && source.charAt(pos) != '"' && source.charAt(pos) != '\n') {
        if (source.charAt(pos) == '\\') pos++;
        pos++;
      }
      pos = Math.min(pos + 1, source.length());
      return new Token(Kind.STRING, source.substring(start, pos), start, pos, startLine);
    }
    if (c == '\\') {
      // Escaped identifier: runs to the next white space.
      while (pos < source.length() && !Character.isWhitespace(source.charAt(pos))) pos++;
      return new Token(Kind.IDENT, source.substring(start, pos), start, pos, startLine);
    }
    if (Character.isLetter(c) || c == '_' || c == '$') {
      while (pos < source.length()) {
        final var d = source.charAt(pos);
        if (!Character.isLetterOrDigit(d) && d != '_' && d != '$') break;
        pos++;
      }
      return new Token(Kind.IDENT, source.substring(start, pos), start, pos, startLine);
    }
    if (Character.isDigit(c) || c == '\'') {
      while (pos < source.length()) {
        final var d = source.charAt(pos);
        if (!Character.isLetterOrDigit(d) && d != '_' && d != '\'' && d != '?') break;
        pos++;
      }
      return new Token(Kind.NUMBER, source.substring(start, pos), start, pos, startLine);
    }
    pos++;
    return new Token(Kind.SYMBOL, String.valueOf(c), start, pos, startLine);
  }
}
