/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.gui;

import static com.cburch.logisim.vhdl.Strings.S;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cburch.logisim.vhdl.base.VerilogContent;
import com.cburch.logisim.vhdl.base.VhdlContent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.text.Segment;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Token;
import org.junit.jupiter.api.Test;

class VerilogTokenMakerTest {

  private static Map<String, Integer> tokens(String line, int initialType) {
    final var result = new LinkedHashMap<String, Integer>();
    final var segment = new Segment(line.toCharArray(), 0, line.length());
    for (var t = new VerilogTokenMaker().getTokenList(segment, initialType, 0);
        t != null && t.isPaintable();
        t = t.getNextToken()) {
      if (t.getType() != Token.WHITESPACE) result.put(t.getLexeme(), t.getType());
    }
    return result;
  }

  @Test
  void highlightsKeywordsTypesNumbersDirectivesAndComments() {
    final var line = tokens("`define W 8 always @(posedge clk) q <= 4'hF; $display(\"x\"); // c", 0);

    assertEquals(Token.PREPROCESSOR, line.get("`define"));
    assertEquals(Token.RESERVED_WORD, line.get("always"));
    assertEquals(Token.RESERVED_WORD, line.get("posedge"));
    assertEquals(Token.IDENTIFIER, line.get("clk"));
    assertEquals(Token.LITERAL_NUMBER_DECIMAL_INT, line.get("4'hF"));
    assertEquals(Token.FUNCTION, line.get("$display"));
    assertEquals(Token.LITERAL_STRING_DOUBLE_QUOTE, line.get("\"x\""));
    assertEquals(Token.COMMENT_EOL, line.get("// c"));
    assertEquals(Token.DATA_TYPE, tokens("wire reg", 0).get("reg"));
  }

  @Test
  void blockCommentsContinueOnTheNextLine() {
    final var maker = new VerilogTokenMaker();
    final var first = "assign a = b; /* starts";
    final var segment = new Segment(first.toCharArray(), 0, first.length());
    maker.getTokenList(segment, Token.NULL, 0);
    assertEquals(Token.COMMENT_MULTILINE, maker.getLastTokenTypeOnLine(segment, Token.NULL));

    final var next = tokens("still */ wire w;", Token.COMMENT_MULTILINE);
    assertEquals(Token.COMMENT_MULTILINE, next.get("still */"));
    assertEquals(Token.DATA_TYPE, next.get("wire"));
  }

  @Test
  void theEditorSwitchesLanguageWithTheEditedModel() throws Exception {
    final var verilog =
        VerilogContent.parse("m", "module m (input a, output q);\nendmodule\n", null);
    final var vhdl =
        VhdlContent.parse(
            "e",
            "entity e is\n  port ( a : in std_logic );\nend e;\narchitecture r of e is\n"
                + "begin\nend r;\n",
            null);
    final var styles = new String[3];
    SwingUtilities.invokeAndWait(
        () -> {
          final var view = new HdlContentView(null);
          view.setHdlModel(verilog);
          styles[0] = view.getSyntaxStyle();
          styles[1] = view.getToolbarModel().getItems().get(0).getToolTip();
          view.setHdlModel(vhdl);
          styles[2] = view.getSyntaxStyle();
        });

    assertEquals(VerilogTokenMaker.SYNTAX_STYLE, styles[0]);
    assertEquals(S.get("verilogOpenButton"), styles[1]);
    assertEquals(SyntaxConstants.SYNTAX_STYLE_VHDL, styles[2]);
  }
}
