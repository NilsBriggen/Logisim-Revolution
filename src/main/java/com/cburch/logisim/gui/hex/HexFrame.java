/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.hex;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.hex.HexEditor;
import com.cburch.hex.HexModel;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.generic.ScrollableForm;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.shell.PanelHeader;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.memory.MemContents;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.WindowMenuItemManager;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class HexFrame extends LFrame.SubWindow {
  private static final long serialVersionUID = 1L;
  private final WindowMenuManager windowManager = new WindowMenuManager();
  private final EditListener editListener = new EditListener();
  private final MyListener myListener = new MyListener();
  private final HexModel model;
  private final HexEditor editor;
  private final JButton open = new JButton();
  private final JButton save = new JButton();
  private final PanelHeader header = new PanelHeader("");

  /**
   * Where to jump to, typed and committed with Enter.
   *
   * <p>The controls used to be a centred FlowLayout strip along the bottom — Open, Save, an
   * "Address:" label, a field, a Go button and a Close Window button — which re-centred and
   * reflowed on every resize, grouped the address box with whatever happened to be beside it, and
   * offered a button to close the window the button was in.
   */
  private final JTextField addressField = new JTextField(12);
  private final Instance instance;

  public HexFrame(Project project, Instance instance, HexModel model) {
    super(project);
    setDefaultCloseOperation(HIDE_ON_CLOSE);

    this.model = model;
    this.editor = new HexEditor(model);
    // A dump of hex digits has to be in a fixed-pitch face or the byte columns do not line up.
    // Nobody had ever given the editor a font, so it inherited the proportional interface one.
    this.editor.setFont(UiFonts.mono());
    this.instance = instance;

    open.setIcon(AppIcons.get(AppIcons.Id.OPEN, 14));
    save.setIcon(AppIcons.get(AppIcons.Id.SAVE, 14));
    for (final var button : new JButton[] {open, save}) {
      button.setBorderPainted(false);
      button.setContentAreaFilled(false);
      button.addActionListener(myListener);
    }
    addressField.addActionListener(myListener);
    addressField.putClientProperty(
        com.formdev.flatlaf.FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT,
        new JLabel(AppIcons.colored(AppIcons.Id.SEARCH, 12, Tokens.mutedForeground())));

    final var actions = new JPanel();
    actions.setOpaque(false);
    actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
    actions.add(open);
    actions.add(save);
    actions.add(Box.createHorizontalStrut(Spacing.md()));
    actions.add(addressField);

    header.setActions(actions);

    final var scroll =
        new JScrollPane(
            editor, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scroll.getViewport().setBackground(editor.getBackground());

    Container contents = getContentPane();
    contents.add(header, BorderLayout.NORTH);
    contents.add(scroll, BorderLayout.CENTER);

    LocaleManager.addLocaleListener(this, myListener);
    myListener.localeChanged();
    ScrollableForm.sizeWindow(this, new Dimension(900, 560), new Dimension(680, 360), true);
    Theme.addListener(getRootPane(), () -> {
      editor.setFont(UiFonts.mono());
      addressField.setFont(UiFonts.mono());
      scroll.getViewport().setBackground(editor.getBackground());
      ScrollableForm.sizeWindow(this, new Dimension(900, 560), new Dimension(680, 360), false);
    });
    addressField.setFont(UiFonts.mono());

    editor.getCaret().addChangeListener(editListener);
    editor.addHistoryListener(editListener);
    editor.getCaret().setDot(0, false);
    editListener.register(menubar);
    setLocationRelativeTo(project.getFrame());
  }

  static long parseAddress(String text, HexModel model) {
    final var address = parseAddress(text);
    if (model == null || address < model.getFirstOffset() || address > model.getLastOffset()) {
      throw new NumberFormatException();
    }
    return address;
  }

  private static long parseAddress(String text) {
    var normalized = text == null ? "" : text.trim();
    if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
      normalized = normalized.substring(2);
    }
    if (normalized.isEmpty()) {
      throw new NumberFormatException();
    }
    return Long.parseLong(normalized, 16);
  }

  public void closeAndDispose() {
    WindowEvent e = new WindowEvent(this, WindowEvent.WINDOW_CLOSING);
    processWindowEvent(e);
    dispose();
  }

  @Override
  public void setVisible(boolean value) {
    if (value && !isVisible()) {
      updateTitle(); // the label may have changed since the window was last shown
      windowManager.frameOpened(this);
    }
    super.setVisible(value);
  }

  /**
   * Names the memory being edited, so that several hex editor windows can be told apart (in the
   * title bar and the Window menu).
   */
  private void updateTitle() {
    final var title = titleFor(instance, project);
    setTitle(title);
    header.setTitle(title);
    windowManager.setText(title);
  }

  static String titleFor(Instance instance, Project project) {
    if (instance == null) return S.get("hexFrameTitle");
    final var label = instance.getAttributeValue(StdAttr.LABEL);
    final var name = instance.getFactory().getDisplayName();
    final var what =
        (label == null || label.isBlank())
            ? S.get("hexFrameTargetAt", name, instance.getLocation().toString())
            : S.get("hexFrameTargetLabel", name, label);
    String circuitName = null;
    if (project != null && project.getLogisimFile() != null) {
      for (final var circuit : project.getLogisimFile().getCircuits()) {
        if (circuit.contains(instance.getComponent())) {
          circuitName = circuit.getName();
          break;
        }
      }
    }
    return circuitName == null
        ? S.get("hexFrameTitleFor", what)
        : S.get("hexFrameTitleForIn", what, circuitName);
  }

  private class EditListener implements ActionListener, ChangeListener {
    private Clip clip = null;

    @Override
    public void actionPerformed(ActionEvent e) {
      Object src = e.getSource();
      if (src == LogisimMenuBar.UNDO) {
        editor.undo();
      } else if (src == LogisimMenuBar.REDO) {
        editor.redo();
      } else if (src == LogisimMenuBar.CUT) {
        getClip().copy();
        editor.delete();
      } else if (src == LogisimMenuBar.COPY) {
        getClip().copy();
      } else if (src == LogisimMenuBar.PASTE) {
        getClip().paste();
      } else if (src == LogisimMenuBar.DELETE) {
        editor.delete();
      } else if (src == LogisimMenuBar.SELECT_ALL) {
        editor.selectAll();
      }
    }

    private void enableItems(LogisimMenuBar menubar) {
      final var sel = editor.selectionExists();
      final var clip = true; // TODO: editor.clipboardExists();
      menubar.setEnabled(LogisimMenuBar.CUT, sel);
      menubar.setEnabled(LogisimMenuBar.COPY, sel);
      menubar.setEnabled(LogisimMenuBar.PASTE, clip);
      menubar.setEnabled(LogisimMenuBar.DELETE, sel);
      menubar.setEnabled(LogisimMenuBar.SELECT_ALL, true);
      menubar.setEnabled(LogisimMenuBar.UNDO, editor.canUndo());
      menubar.setEnabled(LogisimMenuBar.REDO, editor.canRedo());
    }

    private Clip getClip() {
      if (clip == null) clip = new Clip(editor);
      return clip;
    }

    private void register(LogisimMenuBar menubar) {
      // The hex editor keeps its own history: its edits are not project actions, so the
      // project's undo (which the main window offers) would revert something else entirely.
      menubar.addActionListener(LogisimMenuBar.UNDO, this);
      menubar.addActionListener(LogisimMenuBar.REDO, this);
      menubar.addActionListener(LogisimMenuBar.CUT, this);
      menubar.addActionListener(LogisimMenuBar.COPY, this);
      menubar.addActionListener(LogisimMenuBar.PASTE, this);
      menubar.addActionListener(LogisimMenuBar.DELETE, this);
      menubar.addActionListener(LogisimMenuBar.SELECT_ALL, this);
      enableItems(menubar);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
      enableItems((LogisimMenuBar) getJMenuBar());
    }
  }

  private class MyListener implements ActionListener, LocaleListener {
    @Override
    public void actionPerformed(ActionEvent event) {
      final var src = event.getSource();
      if (src == open) {
        HexFile.open((MemContents) model, HexFrame.this, project, instance);
      } else if (src == save) {
        HexFile.save((MemContents) model, HexFrame.this, project, instance);
      } else if (src == addressField) {
        try {
          editor.getCaret().setDot(parseAddress(addressField.getText(), model), false);
          editor.requestFocusInWindow();
        } catch (NumberFormatException e) {
          OptionPane.showMessageDialog(
              HexFrame.this,
              S.get(
                  "hexAddressInvalidMessage",
                  Long.toHexString(model.getFirstOffset()),
                  Long.toHexString(model.getLastOffset())),
              S.get("hexAddressInvalidTitle"),
              OptionPane.ERROR_MESSAGE);
        }
      }
    }

    @Override
    public void localeChanged() {
      updateTitle();
      header.setSubtitle(S.get("hexEditorHint"));
      open.setToolTipText(S.get("openButton"));
      save.setToolTipText(S.get("saveButton"));
      open.getAccessibleContext().setAccessibleName(S.get("openButton"));
      save.getAccessibleContext().setAccessibleName(S.get("saveButton"));
      addressField.getAccessibleContext().setAccessibleName(S.get("hexAddressPlaceholder"));
      addressField.putClientProperty(
          com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT, S.get("hexAddressPlaceholder"));
    }
  }

  private class WindowMenuManager extends WindowMenuItemManager implements LocaleListener {
    WindowMenuManager() {
      super(S.get("hexFrameMenuItem"), false);
      LocaleManager.addLocaleListener(HexFrame.this, this);
    }

    @Override
    public JFrame getJFrame(boolean create, java.awt.Component parent) {
      return HexFrame.this;
    }

    @Override
    public void localeChanged() {
      setText(titleFor(instance, project));
    }
  }
}
