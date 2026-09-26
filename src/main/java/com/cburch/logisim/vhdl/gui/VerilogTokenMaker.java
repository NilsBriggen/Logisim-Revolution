/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.gui;

import javax.swing.text.Segment;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

/**
 * Verilog syntax highlighting for the HDL editor. RSyntaxTextArea ships VHDL but no Verilog
 * support, so this small scanner marks keywords, net types, system tasks, compiler directives,
 * numbers, strings and both comment forms (block comments may span lines).
 */
public class VerilogTokenMaker extends AbstractTokenMaker {
  /** The RSyntaxTextArea style key this token maker is registered under. */
  public static final String SYNTAX_STYLE = "text/verilog";

  private static final String[] KEYWORDS = {
    "always", "and", "assign", "automatic", "begin", "buf", "bufif0", "bufif1", "case", "casex",
    "casez", "cell", "cmos", "config", "deassign", "default", "defparam", "design", "disable",
    "edge", "else", "end", "endcase", "endconfig", "endfunction", "endgenerate", "endmodule",
    "endprimitive", "endspecify", "endtable", "endtask", "event", "for", "force", "forever",
    "fork", "function", "generate", "genvar", "highz0", "highz1", "if", "ifnone", "incdir",
    "include", "initial", "inout", "input", "instance", "join", "large", "liblist", "library",
    "localparam", "macromodule", "medium", "module", "nand", "negedge", "nmos", "nor",
    "noshowcancelled", "not", "notif0", "notif1", "or", "output", "parameter", "pmos",
    "posedge", "primitive", "pull0", "pull1", "pulldown", "pullup", "pulsestyle_ondetect",
    "pulsestyle_onevent", "rcmos", "release", "repeat", "rnmos", "rpmos", "rtran", "rtranif0",
    "rtranif1", "scalared", "showcancelled", "small", "specify", "specparam", "strong0",
    "strong1", "table", "task", "tran", "tranif0", "tranif1", "use", "vectored", "wait",
    "weak0", "weak1", "while", "xnor", "xor"
  };

  private static final String[] TYPES = {
    "integer", "logic", "real", "realtime", "reg", "signed", "supply0", "supply1", "time", "tri",
    "tri0", "tri1", "triand", "trior", "trireg", "unsigned", "uwire", "wand", "wire", "wor"
  };

  @Override
  public TokenMap getWordsToHighlight() {
    final var map = new TokenMap();
    for (final var keyword : KEYWORDS) map.put(keyword, Token.RESERVED_WORD);
    for (final var type : TYPES) map.put(type, Token.DATA_TYPE);
    return map;
  }

  @Override
  public void addToken(Segment segment, int start, int end, int tokenType, int startOffset) {
    if (tokenType == Token.IDENTIFIER) {
      final var value = wordsToHighlight.get(segment, start, end);
      if (value != -1) tokenType = value;
    }
    super.addToken(segment, start, end, tokenType, startOffset);
  }

  @Override
  public String[] getLineCommentStartAndEnd(int languageIndex) {
    return new String[] {"//", null};
  }

  @Override
  public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
    resetTokenList();
    final var array = text.array;
    final var offset = text.offset;
    final var end = offset + text.count;
    // Document offset of array index i is i + shift.
    final var shift = startOffset - offset;

    var i = offset;
    if (initialTokenType == Token.COMMENT_MULTILINE) {
      final var close = indexOfCommentEnd(array, i, end);
      if (close < 0) {
        // The whole line is still inside the comment, which goes on to the next line.
        addToken(text, offset, end - 1, Token.COMMENT_MULTILINE, startOffset);
        return firstToken;
      }
      addToken(text, offset, close + 1, Token.COMMENT_MULTILINE, startOffset);
      i = close + 2;
    }

    while (i < end) {
      final var start = i;
      final var c = array[i];
      final var next = i + 1 < end ? array[i + 1] : '\0';
      if (Character.isWhitespace(c)) {
        while (i < end && Character.isWhitespace(array[i])) i++;
        addToken(text, start, i - 1, Token.WHITESPACE, start + shift);
      } else if (c == '/' && next == '/') {
        addToken(text, start, end - 1, Token.COMMENT_EOL, start + shift);
        i = end;
      } else if (c == '/' && next == '*') {
        final var close = indexOfCommentEnd(array, i + 2, end);
        if (close < 0) {
          addToken(text, start, end - 1, Token.COMMENT_MULTILINE, start + shift);
          return firstToken;
        }
        addToken(text, start, close + 1, Token.COMMENT_MULTILINE, start + shift);
        i = close + 2;
      } else if (c == '"') {
        i++;
        while (i < end && array[i] != '"') {
          if (array[i] == '\\') i++;
          i++;
        }
        i = Math.min(i + 1, end);
        addToken(text, start, i - 1, Token.LITERAL_STRING_DOUBLE_QUOTE, start + shift);
      } else if (c == '`' || c == '$') {
        i = skipIdentifier(array, i + 1, end);
        addToken(
            text, start, i - 1, c == '`' ? Token.PREPROCESSOR : Token.FUNCTION, start + shift);
      } else if (Character.isLetter(c) || c == '_') {
        i = skipIdentifier(array, i + 1, end);
        addToken(text, start, i - 1, Token.IDENTIFIER, start + shift);
      } else if (Character.isDigit(c) || c == '\'') {
        i++;
        while (i < end
            && (Character.isLetterOrDigit(array[i])
                || array[i] == '_'
                || array[i] == '\''
                || array[i] == '?')) {
          i++;
        }
        addToken(text, start, i - 1, Token.LITERAL_NUMBER_DECIMAL_INT, start + shift);
      } else if ("()[]{};,".indexOf(c) >= 0) {
        i++;
        addToken(text, start, start, Token.SEPARATOR, start + shift);
      } else {
        i++;
        addToken(text, start, start, Token.OPERATOR, start + shift);
      }
    }
    addNullToken();
    return firstToken;
  }

  private static int skipIdentifier(char[] array, int from, int end) {
    var i = from;
    while (i < end && (Character.isLetterOrDigit(array[i]) || array[i] == '_' || array[i] == '$')) {
      i++;
    }
    return i;
  }

  /** Index of the '*' of the next "*&#47;" in [from, end), or -1. */
  private static int indexOfCommentEnd(char[] array, int from, int end) {
    for (var i = from; i + 1 < end; i++) {
      if (array[i] == '*' && array[i + 1] == '/') return i;
    }
    return -1;
  }
}
