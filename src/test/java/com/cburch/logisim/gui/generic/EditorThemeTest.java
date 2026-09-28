/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Token;
import org.junit.jupiter.api.Test;

class EditorThemeTest {
  @Test
  void vhdlEditorsDoNotPaintLexerErrorsAsPinkBlocks() {
    final var editor = new RSyntaxTextArea();
    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_VHDL);

    EditorTheme.install(editor, true);

    for (final var token : new int[] {Token.ERROR_IDENTIFIER, Token.ERROR_CHAR}) {
      final var style = editor.getSyntaxScheme().getStyle(token);
      assertNotNull(style);
      assertNull(style.background);
    }
  }
}
