/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.opts;

import static com.cburch.logisim.gui.Strings.S;

import com.bric.colorpicker.ColorPickerDialog;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.gui.generic.FontSelector;
import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.gui.icons.BaseIcon;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.JDialogOk;
import com.cburch.logisim.util.Spacing;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * The project's own style for new components: label font, label colour and the font of new text.
 *
 * <p>Each setting is either the project's choice or "Standard", which is what Logisim does without
 * it. They change only what is placed from now on; components already in the project keep their
 * look. They are saved with the project, so everyone who opens it places components the same way.
 */
class StyleOptions extends OptionsPanel {
  private static final long serialVersionUID = 1L;

  private final Setting<Font> labelFont =
      new Setting<>(Options.ATTR_LABEL_FONT, StdAttr.DEFAULT_LABEL_FONT, this::chooseFont);
  private final Setting<Color> labelColor =
      new Setting<>(Options.ATTR_LABEL_COLOR, StdAttr.DEFAULT_LABEL_COLOR, this::chooseColor);
  private final Setting<Font> textFont =
      new Setting<>(Options.ATTR_TEXT_FONT, StdAttr.DEFAULT_LABEL_FONT, this::chooseFont);
  private final JTextArea hint;

  StyleOptions(OptionsFrame window) {
    super(window);
    final var form = new SettingsForm();
    form.addRow(labelFont.caption, labelFont.row);
    form.addRow(labelColor.caption, labelColor.row);
    form.addRow(textFont.caption, textFont.row);
    hint = form.addHint("");
    setLayout(new BorderLayout());
    add(form, BorderLayout.NORTH);

    final AttributeListener listener =
        new AttributeListener() {
          @Override
          public void attributeValueChanged(AttributeEvent event) {
            refresh();
          }
        };
    getOptions().getAttributeSet().addAttributeListener(listener);
    localeChanged();
  }

  @Override
  public String getHelpText() {
    return S.get("styleHelp");
  }

  @Override
  public String getTitle() {
    return S.get("styleTitle");
  }

  @Override
  public void localeChanged() {
    labelFont.caption.setText(S.get("styleLabelFont"));
    labelColor.caption.setText(S.get("styleLabelColor"));
    textFont.caption.setText(S.get("styleTextFont"));
    hint.setText(S.get("styleHint"));
    refresh();
  }

  private void refresh() {
    labelFont.refresh();
    labelColor.refresh();
    textFont.refresh();
  }

  /** Opens the font picker on {@code current}; hands the choice on, or nothing when cancelled. */
  private void chooseFont(Setting<Font> setting, Font current, Consumer<Font> chosen) {
    final var selector = new FontSelector();
    try {
      selector.setValue(current);
    } catch (IllegalArgumentException missingFamily) {
      // The project names a font this computer lacks: start from the standard one.
      selector.setValue(StdAttr.DEFAULT_LABEL_FONT);
    }
    final var result = new Font[1];
    final var dialog =
        new JDialogOk(setting.caption.getText().replaceFirst("\\s*:\\s*$", "")) {
          private static final long serialVersionUID = 1L;

          @Override
          public void okClicked() {
            result[0] = (Font) selector.getValue();
          }
        };
    final var content = new JPanel(new BorderLayout());
    content.setBorder(Spacing.panelBorder());
    content.add(selector, BorderLayout.CENTER);
    dialog.getContentPane().add(content, BorderLayout.CENTER);
    dialog.pack();
    dialog.setMinimumSize(dialog.getSize());
    dialog.setResizable(true);
    dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
    dialog.setVisible(true);
    if (result[0] != null) chosen.accept(result[0]);
  }

  private void chooseColor(Setting<Color> setting, Color current, Consumer<Color> chosen) {
    final var picked = ColorPickerDialog.showDialog(getOptionsFrame(), current, false);
    if (picked != null) chosen.accept(picked);
  }

  /** How a setting's value is picked: from the value shown, handing on the new one. */
  private interface Chooser<V> {
    void choose(Setting<V> setting, V current, Consumer<V> chosen);
  }

  /** One row: a caption, a button showing the value that opens its picker, and a reset. */
  private final class Setting<V> {
    private final Attribute<V> attr;
    private final V standard;
    final JLabel caption = new JLabel();
    final JPanel row = new JPanel(new BorderLayout(Spacing.sm(), 0));
    private final JButton value = new JButton();
    private final JButton reset = new JButton();

    Setting(Attribute<V> attr, V standard, Chooser<V> chooser) {
      this.attr = attr;
      this.standard = standard;
      caption.setLabelFor(value);
      row.setOpaque(false);
      row.add(value, BorderLayout.CENTER);
      row.add(reset, BorderLayout.EAST);
      value.setHorizontalAlignment(JButton.LEADING);
      value.addActionListener(
          event -> chooser.choose(this, current(), picked -> set(picked)));
      reset.addActionListener(event -> set(null));
    }

    /** The project's value, or {@code null} when it has none and the standard applies. */
    private V projectValue() {
      return getOptions().getAttributeSet().getValue(attr);
    }

    private V current() {
      final var own = projectValue();
      return own == null ? standard : own;
    }

    private void set(V newValue) {
      final var attrs = getOptions().getAttributeSet();
      getProject().doAction(OptionsActions.setAttribute(attrs, attr, newValue));
    }

    void refresh() {
      final var own = projectValue();
      final var shown = own == null ? standard : own;
      final String text;
      if (shown instanceof Color color) {
        text = own == null ? S.get("styleStandardValue") : hex(color);
        value.setIcon(new Swatch(own == null ? CanvasStyle.labelColor(color) : color));
      } else {
        final var description = attr.toDisplayString(shown);
        text = own == null ? S.get("styleStandardFont", description) : description;
        value.setIcon(null);
      }
      value.setText(text);
      value.getAccessibleContext().setAccessibleDescription(text);
      reset.setText(S.get("styleUseStandard"));
      reset.setToolTipText(S.get("styleUseStandardTip"));
      reset.setEnabled(own != null);
    }
  }

  private static String hex(Color color) {
    return String.format("#%06X", color.getRGB() & 0xFFFFFF);
  }

  /** A small rounded colour sample, outlined so white and black both show. */
  private static final class Swatch extends BaseIcon {
    private final Color color;

    Swatch(Color color) {
      this.color = color;
    }

    @Override
    protected void paintIcon(Graphics2D g2) {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      final var width = getIconWidth();
      final var height = getIconHeight();
      g2.setColor(color);
      g2.fillRoundRect(0, 0, width, height, 4, 4);
      g2.setColor(Tokens.divider());
      g2.drawRoundRect(0, 0, width - 1, height - 1, 4, 4);
    }
  }
}
