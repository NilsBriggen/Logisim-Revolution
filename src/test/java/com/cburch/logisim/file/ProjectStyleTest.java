/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.base.Text;
import com.cburch.logisim.std.io.Led;
import java.awt.Color;
import java.awt.Font;
import org.junit.jupiter.api.Test;

/**
 * The project's style reaches new components only where the tool still has the shipped value, and
 * with nothing set placing a component is exactly what it was.
 */
class ProjectStyleTest {

  private static final Font SERIF = new Font("Serif", Font.ITALIC, 11);
  private static final Font MONO = new Font("Monospaced", Font.PLAIN, 22);
  private static final Color GREEN = new Color(0x10, 0x90, 0x30);
  private static final Led LED = new Led();

  @Test
  void withNothingSetNewComponentsKeepTheShippedStyle() {
    final var attrs = LED.createAttributeSet();
    ProjectStyle.applyToNewComponent(new Options(), LED, attrs);
    assertEquals(StdAttr.DEFAULT_LABEL_FONT, attrs.getValue(StdAttr.LABEL_FONT));
    assertEquals(StdAttr.DEFAULT_LABEL_COLOR, attrs.getValue(StdAttr.LABEL_COLOR));
  }

  @Test
  void newComponentsGetTheProjectsLabelFontAndColour() {
    final var options = styled();
    final var attrs = LED.createAttributeSet();
    ProjectStyle.applyToNewComponent(options, LED, attrs);
    assertEquals(SERIF, attrs.getValue(StdAttr.LABEL_FONT));
    assertEquals(GREEN, attrs.getValue(StdAttr.LABEL_COLOR));
  }

  @Test
  void valueChosenOnTheToolWins() {
    final var own = new Font("Dialog", Font.PLAIN, 30);
    final var attrs = LED.createAttributeSet();
    attrs.setValue(StdAttr.LABEL_FONT, own);
    ProjectStyle.applyToNewComponent(styled(), LED, attrs);
    assertEquals(own, attrs.getValue(StdAttr.LABEL_FONT));
    assertEquals(GREEN, attrs.getValue(StdAttr.LABEL_COLOR), "the colour was still shipped");
  }

  @Test
  void newTextGetsTheProjectsTextFontAndNotTheLabelFont() {
    final var attrs = Text.FACTORY.createAttributeSet();
    ProjectStyle.applyToNewText(styled(), attrs);
    assertEquals(MONO, attrs.getValue(Text.ATTR_FONT));
  }

  @Test
  void componentsWithoutLabelsAreLeftAlone() {
    final var attrs = com.cburch.logisim.std.wiring.Constant.FACTORY.createAttributeSet();
    final var before = attrs.getAttributes();
    ProjectStyle.applyToNewComponent(
        styled(), com.cburch.logisim.std.wiring.Constant.FACTORY, attrs);
    assertEquals(before, attrs.getAttributes());
  }

  private static Options styled() {
    final var options = new Options();
    final var attrs = options.getAttributeSet();
    attrs.setValue(Options.ATTR_LABEL_FONT, SERIF);
    attrs.setValue(Options.ATTR_LABEL_COLOR, GREEN);
    attrs.setValue(Options.ATTR_TEXT_FONT, MONO);
    return options;
  }
}
