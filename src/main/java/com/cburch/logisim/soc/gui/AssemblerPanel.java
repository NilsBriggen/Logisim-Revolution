/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.soc.gui;

import static com.cburch.logisim.soc.Strings.S;

import com.cburch.contracts.BaseDocumentListenerContract;
import com.cburch.contracts.BaseKeyListenerContract;
import com.cburch.contracts.BaseMouseListenerContract;
import com.cburch.contracts.BaseWindowListenerContract;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.gui.generic.EditorTheme;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.icons.CompileIcon;
import com.cburch.logisim.gui.icons.ErrorIcon;
import com.cburch.logisim.gui.icons.InfoIcon;
import com.cburch.logisim.gui.icons.OpenSaveIcon;
import com.cburch.logisim.gui.icons.RunIcon;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectCloseConfirmation;
import com.cburch.logisim.soc.data.SocProcessorInterface;
import com.cburch.logisim.soc.util.Assembler;
import com.cburch.logisim.soc.util.AssemblerInterface;
import com.cburch.logisim.util.LocaleListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.function.BooleanSupplier;
import javax.swing.Box;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

public class AssemblerPanel extends JPanel
    implements BaseMouseListenerContract,
        LocaleListener,
        ActionListener,
        BaseKeyListenerContract,
        BaseDocumentListenerContract,
        CaretListener,
        BaseWindowListenerContract {

  private static final long serialVersionUID = 1L;

  private final Assembler assembler;
  private final RSyntaxTextArea asmWindow;
  private final RTextScrollPane debugScrollPane;
  private final JLabel compileLabel = new JLabel();
  private final JLabel openLabel = new JLabel();
  private final JLabel saveLabel = new JLabel();
  private final JLabel saveAsLabel = new JLabel();
  private final JLabel runLabel = new JLabel();
  private final JLabel helpLabel = new JLabel();
  private final JLabel nextErrorLabel = new JLabel();
  private final JLabel prevErrorLabel = new JLabel();
  private final JLabel lineLabel = new JLabel();
  private final JMenuItem openMenuItem = new JMenuItem();
  private final JMenuItem saveMenuItem = new JMenuItem();
  private final JMenuItem saveAsMenuItem = new JMenuItem();
  private final ListeningFrame parent;
  private final SocProcessorInterface cpu;
  private final CircuitState circuitState;
  private final Project project;
  private final BooleanSupplier closeGuard = this::confirmDiscardChanges;
  private int lineNumber = 1;
  private int numberOfLines = 1;
  private boolean documentChanged = false;
  private File textFile;

  public AssemblerPanel(
      ListeningFrame parent,
      String highLiter,
      AssemblerInterface assembler,
      SocProcessorInterface cpu,
      CircuitState state,
      Project project) {
    parent.addWindowListener(this);
    // Closing is decided in windowClosing, so that unsaved source can be kept (Cancel).
    parent.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    this.parent = parent;
    this.project = project;
    this.cpu = cpu;
    circuitState = state;
    textFile = null;
    asmWindow = new RSyntaxTextArea(20, 60);
    asmWindow.setSyntaxEditingStyle(highLiter);
    asmWindow.setEditable(true);
    asmWindow.addKeyListener(this);
    asmWindow.getDocument().addDocumentListener(this);
    asmWindow.addCaretListener(this);
    JPopupMenu popUp = asmWindow.getPopupMenu();
    popUp.remove(popUp.getComponentCount() - 1);
    popUp.add(openMenuItem);
    openMenuItem.addActionListener(this);
    popUp.add(saveMenuItem);
    saveMenuItem.addActionListener(this);
    popUp.add(saveAsMenuItem);
    saveAsMenuItem.addActionListener(this);
    asmWindow.setPopupMenu(popUp);
    debugScrollPane = new RTextScrollPane(asmWindow);
    debugScrollPane.setLineNumbersEnabled(true);
    debugScrollPane.setIconRowHeaderEnabled(true);
    debugScrollPane.getGutter().setBookmarkingEnabled(false);
    Box info = Box.createHorizontalBox();
    openLabel.setIcon(new OpenSaveIcon(OpenSaveIcon.FILE_OPEN));
    openLabel.addMouseListener(this);
    info.add(openLabel);
    saveLabel.setIcon(new OpenSaveIcon(OpenSaveIcon.FILE_SAVE));
    saveLabel.addMouseListener(this);
    info.add(Box.createHorizontalStrut(5));
    info.add(saveLabel);
    saveAsLabel.setIcon(new OpenSaveIcon(OpenSaveIcon.FILE_SAVE_AS));
    saveAsLabel.addMouseListener(this);
    info.add(Box.createHorizontalStrut(5));
    info.add(saveAsLabel);
    compileLabel.setIcon(new CompileIcon());
    compileLabel.addMouseListener(this);
    info.add(Box.createHorizontalStrut(5));
    info.add(compileLabel);
    prevErrorLabel.setIcon(new ErrorIcon(false, true));
    prevErrorLabel.addMouseListener(this);
    info.add(Box.createHorizontalStrut(5));
    info.add(prevErrorLabel);
    nextErrorLabel.setIcon(new ErrorIcon(true, false));
    nextErrorLabel.addMouseListener(this);
    info.add(Box.createHorizontalStrut(5));
    info.add(nextErrorLabel);
    runLabel.setIcon(new RunIcon());
    runLabel.addMouseListener(this);
    info.add(Box.createHorizontalStrut(5));
    info.add(runLabel);
    helpLabel.setIcon(new InfoIcon());
    helpLabel.addMouseListener(this);
    info.add(Box.createHorizontalStrut(5));
    info.add(helpLabel);
    info.add(Box.createHorizontalGlue());
    info.add(lineLabel);
    info.setPreferredSize(new Dimension(40, AppPreferences.getScaled(20)));
    setLayout(new BorderLayout());
    info.add(Box.createHorizontalStrut(5));
    add(info, BorderLayout.NORTH);
    add(debugScrollPane);
    this.assembler = new Assembler(assembler, debugScrollPane);
    asmWindow.addParser(this.assembler);
    localeChanged();
    EditorTheme.install(asmWindow);
  }

  /**
   * Offers Save, Discard or Cancel when the source has unsaved changes.
   *
   * @return true when it is fine to drop the current text (it was saved, discarded or unchanged)
   */
  public boolean confirmDiscardChanges() {
    return ProjectCloseConfirmation.confirm(
        documentChanged,
        () -> {
          parent.setVisible(true);
          parent.toFront();
          final String[] options = {
            S.get("AsmPanSaveOption"), S.get("AsmPanDiscardOption"), S.get("AsmPanCancelOption")
          };
          return OptionPane.showOptionDialog(
              parent,
              S.get("AsmPanDocumentChangedSave"),
              parent.getParentTitle(),
              OptionPane.YES_NO_CANCEL_OPTION,
              OptionPane.QUESTION_MESSAGE,
              null,
              options,
              options[0]);
        },
        () -> saveFile(false));
  }

  public boolean isDocumentChanged() {
    return documentChanged;
  }

  private void openFile() {
    if (!confirmDiscardChanges()) return;
    JFileChooser chooser = new JFileChooser();
    FileNameExtensionFilter filter =
        new FileNameExtensionFilter(S.get("AsmPanAmsFileExtention"), "S", "asm");
    chooser.setDialogTitle(parent.getParentTitle() + ": " + S.get("AsmPanReadAsmFile"));
    chooser.setFileFilter(filter);
    int ret = chooser.showOpenDialog(parent);
    if (ret != JFileChooser.APPROVE_OPTION) return;
    textFile = chooser.getSelectedFile();
    try {
      BufferedReader reader = new BufferedReader(new FileReader(textFile));
      StringBuilder s = new StringBuilder();
      String st;
      while ((st = reader.readLine()) != null) s.append(st).append("\n");
      reader.close();
      asmWindow.setText(s.toString());
    } catch (IOException e) {
      OptionPane.showMessageDialog(
          parent,
          S.get("AsmPanErrorReadingFile", textFile.getName()),
          parent.getParentTitle(),
          OptionPane.ERROR_MESSAGE);
      textFile = null;
      return;
    }
    asmWindow.setCaretPosition(0);
    documentChanged = false;
    assembler.reset();
    if (!assembler.assemble()) {
      asmWindow.setCaretPosition(assembler.getErrorPositions().get(0));
    }
    updateLineNumber();
  }

  /** Saves the source; returns false when the user cancelled or the file could not be written. */
  private boolean saveFile(boolean askFileName) {
    if (!documentChanged && !askFileName) return true;
    if (askFileName || textFile == null) {
      JFileChooser chooser = new JFileChooser();
      FileNameExtensionFilter filter =
          new FileNameExtensionFilter(S.get("AsmPanAmsFileExtention"), "S", "asm");
      chooser.setDialogTitle(parent.getParentTitle() + ": " + S.get("AsmPanSaveAsmFile"));
      chooser.setFileFilter(filter);
      if (textFile != null) chooser.setSelectedFile(textFile);
      int ret = chooser.showSaveDialog(parent);
      if (ret != JFileChooser.APPROVE_OPTION) return false;
      textFile = chooser.getSelectedFile();
    }
    try {
      BufferedWriter writer =
          new BufferedWriter(new OutputStreamWriter(new FileOutputStream(textFile)));
      writer.write(asmWindow.getText());
      writer.close();
    } catch (IOException e) {
      OptionPane.showMessageDialog(
          parent,
          S.get("AsmPanErrorCreateFile", textFile.getName()),
          parent.getParentTitle(),
          OptionPane.ERROR_MESSAGE);
      return false;
    }
    documentChanged = false;
    updateLineNumber();
    return true;
  }

  private boolean assemble(boolean showWindow) {
    boolean result = assembler.assemble();
    if (!result) asmWindow.setCaretPosition(assembler.getErrorPositions().get(0));
    else if (showWindow) OptionPane.showMessageDialog(parent, S.get("AssemblerAssembleSuccess"));
    return result;
  }

  private void runProgram() {
    if (!assemble(false)) return;
    long entryPoint = assembler.getEntryPoint();
    if (entryPoint < 0) return;
    if (!assembler.download(cpu, circuitState)) {
      OptionPane.showMessageDialog(
          parent, S.get("AssemblerUnableToDownload"), S.get("AsmPanRun"), OptionPane.ERROR_MESSAGE);
      return;
    }
    cpu.setEntryPointandReset(circuitState, entryPoint, null, assembler.getSectionHeader());
    OptionPane.showMessageDialog(
        parent, S.get("AssemblerRunSuccess"), S.get("AsmPanRun"), OptionPane.INFORMATION_MESSAGE);
  }

  private void updateLineNumber() {
    lineLabel.setText(S.get("RV32imAsmLineIndicator", lineNumber, numberOfLines));
    lineLabel.repaint();
  }

  private void nextError(boolean after) {
    final var carretPos = asmWindow.getCaretPosition();
    final var errorPositions = assembler.getErrorPositions();
    if (errorPositions.isEmpty()) return;
    int findex = -1;
    int index = 0;
    while (findex < 0 && index < errorPositions.size()) {
      if (carretPos <= errorPositions.get(index)) findex = index;
      index++;
    }
    if (after) {
      if (findex < 0 || findex == (errorPositions.size() - 1))
        asmWindow.setCaretPosition(errorPositions.get(0));
      else asmWindow.setCaretPosition(errorPositions.get(findex + 1));
    } else {
      if (findex <= 0) asmWindow.setCaretPosition(errorPositions.get(errorPositions.size() - 1));
      else asmWindow.setCaretPosition(errorPositions.get(findex - 1));
    }
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    final var source = e.getSource();
    if (source == openLabel) openFile();
    else if (source == saveLabel) saveFile(false);
    else if (source == saveAsLabel) saveFile(true);
    else if (source == compileLabel) assemble(true);
    else if (source == nextErrorLabel) nextError(true);
    else if (source == prevErrorLabel) nextError(false);
    else if (source == runLabel) runProgram();
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    Object source = e.getSource();
    if (source == openMenuItem) openFile();
    else if (source == saveMenuItem) saveFile(false);
    else if (source == saveAsMenuItem) saveFile(true);
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (!e.isAltDown() && e.isControlDown() && e.getKeyCode() == KeyEvent.VK_S) saveFile(false);
    else if (!e.isAltDown() && e.isControlDown() && e.getKeyCode() == KeyEvent.VK_L) openFile();
    else if (e.isAltDown() && !e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) assemble(true);
    else if (e.isAltDown() && !e.isControlDown() && e.getKeyCode() == KeyEvent.VK_R) runProgram();
    else if (!e.isAltDown() && e.isControlDown() && e.getKeyCode() == KeyEvent.VK_N)
      nextError(true);
    else if (!e.isAltDown() && e.isControlDown() && e.getKeyCode() == KeyEvent.VK_P)
      nextError(false);
  }

  @Override
  public void localeChanged() {
    openLabel.setToolTipText(S.get("AsmPanOpenFile"));
    saveLabel.setToolTipText(S.get("AsmPanSaveFile"));
    saveAsLabel.setToolTipText(S.get("AsmPanSaveFileAs"));
    compileLabel.setToolTipText(S.get("AsmPanAssemble"));
    nextErrorLabel.setToolTipText(S.get("AsmPanNextError"));
    prevErrorLabel.setToolTipText(S.get("AsmPanPreviousError"));
    runLabel.setToolTipText(S.get("AsmPanRun"));
    openMenuItem.setText(S.get("AsmPanOpenFile"));
    saveMenuItem.setText(S.get("AsmPanSaveFile"));
    saveAsMenuItem.setText(S.get("AsmPanSaveFileAs"));
    updateLineNumber();
  }

  @Override
  public void insertUpdate(DocumentEvent e) {
    documentChanged = true;
    assembler.checkAndBuildTokens(asmWindow.getCaretLineNumber());
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    documentChanged = true;
    assembler.checkAndBuildTokens(asmWindow.getCaretLineNumber());
  }

  @Override
  public void caretUpdate(CaretEvent e) {
    numberOfLines = asmWindow.getDocument().getDefaultRootElement().getElementCount();
    lineNumber = asmWindow.getCaretLineNumber() + 1;
    updateLineNumber();
  }

  @Override
  public void windowOpened(WindowEvent e) {
    // The frame is reused after being closed, so the guard follows its open/closed life cycle.
    if (project != null) project.addCloseGuard(closeGuard);
  }

  @Override
  public void windowClosing(WindowEvent e) {
    if (confirmDiscardChanges()) {
      parent.setVisible(false);
      parent.dispose();
    }
  }

  @Override
  public void windowClosed(WindowEvent e) {
    if (project != null) project.removeCloseGuard(closeGuard);
    if (documentChanged) {
      // Disposed without asking (the processor was removed): offer a last chance to save.
      parent.setVisible(true);
      int ret =
          OptionPane.showConfirmDialog(
              parent,
              S.get("AsmPanDocumentChangedSave"),
              parent.getParentTitle(),
              OptionPane.YES_NO_OPTION);
      if (ret == OptionPane.YES_OPTION) saveFile(false);
      parent.setVisible(false);
    }
    asmWindow.setText("");
    assembler.reset();
    documentChanged = false;
    updateLineNumber();
  }
}
