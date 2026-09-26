/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.tools;

import static com.cburch.logisim.tools.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.EditLockAction;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.gui.menu.ComponentHelp;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import javax.swing.JMenu;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class MenuTool extends Tool {
  /**
   * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
   * prevent project files from loading.
   *
   * <p>Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "Menu Tool";

  private static class MenuComponent extends JPopupMenu implements ActionListener {
    private static final long serialVersionUID = 1L;
    final Project proj;
    final Circuit circ;
    final Component comp;
    final JMenuItem del = new JMenuItem(S.get("compDeleteItem"));
    final JMenuItem attrs = new JMenuItem(S.get("compShowAttrItem"));
    final JMenuItem rotateRight = new JMenuItem(S.get("compRotateRight"));
    final JMenuItem rotateLeft = new JMenuItem(S.get("compRotateLeft"));
    final JMenuItem help = new JMenuItem();
    final JMenuItem lock;

    MenuComponent(Project proj, Circuit circ, Component comp) {
      this.proj = proj;
      this.circ = circ;
      this.comp = comp;
      help.setText(
          S.get("compHelpItemFor", comp.getFactory().getDisplayGetter().toString()));
      boolean canChange = proj.getLogisimFile().contains(circ);
      // A locked component offers no edits it would only refuse.
      final var editable = canChange && !circ.isEditLockedFor(comp);
      final var locked = circ.isComponentEditLocked(comp);
      lock = lockItem(locked ? "compUnlockItem" : "compLockItem", locked);

      if (comp.getAttributeSet().containsAttribute(StdAttr.FACING)) {
        add(rotateLeft);
        rotateLeft.addActionListener(this);
        rotateLeft.setEnabled(editable);
        add(rotateRight);
        rotateRight.addActionListener(this);
        rotateRight.setEnabled(editable);
      }

      add(del);
      del.addActionListener(this);
      del.setEnabled(editable);
      add(attrs);
      attrs.addActionListener(this);
      if (!(comp instanceof Wire)) {
        addSeparator();
        add(lock);
        lock.addActionListener(this);
        lock.setEnabled(canChange);
      }
      addSeparator();
      add(help);
      help.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      Object src = e.getSource();
      if (src == del) {
        final var circ = proj.getCurrentCircuit();
        final var xn = new CircuitMutation(circ);
        xn.remove(comp);
        proj.doAction(
            xn.toAction(S.getter("removeComponentAction", comp.getFactory().getDisplayGetter())));
      } else if (src == attrs) {
        proj.getFrame().viewComponentAttributes(circ, comp);
      } else if (src == lock) {
        proj.doAction(
            EditLockAction.setComponentsLocked(
                circ, List.of(comp), !circ.isComponentEditLocked(comp)));
      } else if (src == help) {
        if (proj.getFrame().getJMenuBar() instanceof LogisimMenuBar menuBar) {
          final var target = ComponentHelp.getHelpTarget(comp.getFactory(), proj.getLogisimFile());
          menuBar.help.showHelp(target);
        }
      } else if (src == rotateRight) {
        final var circ = proj.getCurrentCircuit();
        final var xn = new CircuitMutation(circ);
        final var d = comp.getAttributeSet().getValue(StdAttr.FACING);
        xn.set(comp, StdAttr.FACING, d.getRight());
        proj.doAction(
            xn.toAction(S.getter("rotateComponentAction", comp.getFactory().getDisplayGetter())));
      } else if (src == rotateLeft) {
        final var circ = proj.getCurrentCircuit();
        final var xn = new CircuitMutation(circ);
        final var d = comp.getAttributeSet().getValue(StdAttr.FACING);
        xn.set(comp, StdAttr.FACING, d.getLeft());
        proj.doAction(
            xn.toAction(S.getter("rotateComponentAction", comp.getFactory().getDisplayGetter())));
      }
    }
  }

  private static class MenuSelection extends JPopupMenu implements ActionListener {
    private static final long serialVersionUID = 1L;
    final Project proj;
    final JMenuItem del = new JMenuItem(S.get("selDeleteItem"));
    final JMenuItem cut = new JMenuItem(S.get("selCutItem"));
    final JMenuItem copy = new JMenuItem(S.get("selCopyItem"));
    final JMenuItem lock;

    MenuSelection(Project proj) {
      this.proj = proj;
      final var circ = proj.getCurrentCircuit();
      boolean canChange = proj.getLogisimFile().contains(circ);
      final var selected = proj.getSelection().getComponents();
      var anyLocked = circ.isEditLocked();
      for (final var comp : selected) anyLocked |= circ.isComponentEditLocked(comp);
      final var allLocked = EditLockAction.allLocked(circ, selected);
      lock = lockItem(allLocked ? "selUnlockItem" : "selLockItem", allLocked);
      add(del);
      del.addActionListener(this);
      del.setEnabled(canChange && !anyLocked);
      add(cut);
      cut.addActionListener(this);
      cut.setEnabled(canChange && !anyLocked);
      add(copy);
      copy.addActionListener(this);
      addSeparator();
      add(lock);
      lock.addActionListener(this);
      lock.setEnabled(canChange && selected.stream().anyMatch(comp -> !(comp instanceof Wire)));
      addArrangeMenu();
    }

    /** Edit &gt; Arrange, run through the menu bar so the two stay in step. */
    private void addArrangeMenu() {
      final var frame = proj.getFrame();
      if (frame == null || !(frame.getJMenuBar() instanceof LogisimMenuBar menuBar)) return;
      final var arrange = new JMenu(com.cburch.logisim.gui.Strings.S.get("editArrangeMenu"));
      var anyEnabled = false;
      for (final var item : LogisimMenuBar.ARRANGE_ITEMS) {
        if (item == LogisimMenuBar.DISTRIBUTE_HORIZONTAL) arrange.addSeparator();
        final var entry = new JMenuItem(LogisimMenuBar.arrangeItemText(item));
        final var enabled = menuBar.isEnabled(item);
        entry.setEnabled(enabled);
        anyEnabled |= enabled;
        entry.addActionListener(e -> menuBar.doAction(item));
        arrange.add(entry);
      }
      arrange.setEnabled(anyEnabled);
      addSeparator();
      add(arrange);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      Object src = e.getSource();
      final var sel = proj.getSelection();
      if (src == del) {
        proj.doAction(SelectionActions.clear(sel));
      } else if (src == cut) {
        proj.doAction(SelectionActions.cut(sel));
      } else if (src == copy) {
        proj.doAction(SelectionActions.copy(sel));
      } else if (src == lock) {
        final var circ = proj.getCurrentCircuit();
        final var comps = sel.getComponents();
        proj.doAction(
            EditLockAction.setComponentsLocked(circ, comps, !EditLockAction.allLocked(circ, comps)));
      }
    }

    /*
     * public void show(JComponent parent, int x, int y) { super.show(this,
     * x, y); }
     */
  }

  public MenuTool() {}

  /** "Lock ..." with a closed padlock, or "Unlock ..." with an open one. */
  private static JMenuItem lockItem(String key, boolean locked) {
    final var item = new JMenuItem(S.get(key));
    item.setIcon(AppIcons.get(locked ? AppIcons.Id.UNLOCK : AppIcons.Id.LOCK, AppIcons.SIZE));
    return item;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof MenuTool;
  }

  @Override
  public String getDescription() {
    return S.get("menuToolDesc");
  }

  @Override
  public String getDisplayName() {
    return S.get("menuTool");
  }

  @Override
  public int hashCode() {
    return MenuTool.class.hashCode();
  }

  @Override
  public void mousePressed(Canvas canvas, Graphics g, MouseEvent e) {
    int x = e.getX();
    int y = e.getY();
    final var pt = Location.create(x, y, false);

    JPopupMenu menu;
    final var proj = canvas.getProject();
    final var sel = proj.getSelection();
    final var inSel = sel.getComponentsContaining(pt, g);
    if (!inSel.isEmpty()) {
      final var comp = inSel.iterator().next();
      if (sel.getComponents().size() > 1) {
        menu = new MenuSelection(proj);
      } else {
        menu = new MenuComponent(proj, canvas.getCircuit(), comp);
        final var extender = (MenuExtender) comp.getFeature(MenuExtender.class);
        if (extender != null) extender.configureMenu(menu, proj);
      }
    } else {
      final var cl = canvas.getCircuit().getAllContaining(pt, g);
      if (!cl.isEmpty()) {
        final var comp = cl.iterator().next();
        menu = new MenuComponent(proj, canvas.getCircuit(), comp);
        final var extender = (MenuExtender) comp.getFeature(MenuExtender.class);
        if (extender != null) extender.configureMenu(menu, proj);
      } else {
        menu = null;
      }
    }

    if (menu != null) {
      canvas.showPopupMenu(menu, x, y);
    }
  }

  @Override
  public void paintIcon(ComponentDrawContext c, int x, int y) {
    AppIcons.get(AppIcons.Id.MORE, AppPreferences.IconSize)
        .paintIcon(c.getDestination(), c.getGraphics(), x, y);
  }
}
