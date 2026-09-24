/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.draw.toolbar.AbstractToolbarModel;
import com.cburch.draw.toolbar.ToolbarItem;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.menu.MenuListener;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.util.UnmodifiableList;
import java.util.List;

/** The actions for the project's own circuits: add one, reorder them, edit or remove one. */
class ToolboxToolbarModel extends AbstractToolbarModel implements MenuListener.EnabledListener {

  /** Small enough to sit in a panel heading rather than take a row of its own. */
  private static final int ICON_SIZE = 15;

  private final Frame frame;
  private final LogisimToolbarItem itemAdd;
  private final LogisimToolbarItem itemAddVhdl;
  private final LogisimToolbarItem itemUp;
  private final LogisimToolbarItem itemDown;
  private final LogisimToolbarItem itemAppearance;
  private final LogisimToolbarItem itemDelete;
  private final List<ToolbarItem> items;

  public ToolboxToolbarModel(Frame frame, MenuListener menu) {
    this.frame = frame;
    itemAdd =
        new LogisimToolbarItem(
            menu,
            AppIcons.get(AppIcons.Id.ADD, ICON_SIZE),
            LogisimMenuBar.ADD_CIRCUIT,
            S.getter("projectAddCircuitTip"));
    itemAddVhdl =
        new LogisimToolbarItem(
            menu,
            AppIcons.get(AppIcons.Id.HDL, ICON_SIZE),
            LogisimMenuBar.ADD_VHDL,
            S.getter("projectAddVhdlItem"));
    itemUp =
        new LogisimToolbarItem(
            menu,
            AppIcons.get(AppIcons.Id.MOVE_UP, ICON_SIZE),
            LogisimMenuBar.MOVE_CIRCUIT_UP,
            S.getter("projectMoveCircuitUpTip"));
    itemDown =
        new LogisimToolbarItem(
            menu,
            AppIcons.get(AppIcons.Id.MOVE_DOWN, ICON_SIZE),
            LogisimMenuBar.MOVE_CIRCUIT_DOWN,
            S.getter("projectMoveCircuitDownTip"));
    itemAppearance =
        new LogisimToolbarItem(
            menu,
            AppIcons.get(AppIcons.Id.APPEARANCE, ICON_SIZE),
            LogisimMenuBar.TOGGLE_APPEARANCE,
            S.getter("projectEditAppearanceTip"));
    itemDelete =
        new LogisimToolbarItem(
            menu,
            AppIcons.get(AppIcons.Id.DELETE, ICON_SIZE),
            LogisimMenuBar.REMOVE_CIRCUIT,
            S.getter("projectRemoveCircuitTip"));

    items =
        UnmodifiableList.create(
            new ToolbarItem[] {
              itemAdd, itemAddVhdl, itemUp, itemDown, itemAppearance, itemDelete,
            });

    menu.addEnabledListener(this);
  }

  @Override
  public List<ToolbarItem> getItems() {
    return items;
  }

  @Override
  public boolean isSelected(ToolbarItem item) {
    return (item == itemAppearance) && frame.getEditorView().equals(Frame.EDIT_APPEARANCE);
  }

  @Override
  public void itemSelected(ToolbarItem item) {
    if (item instanceof LogisimToolbarItem toolbarItem) toolbarItem.doAction();
  }

  @Override
  public void menuEnableChanged(MenuListener source) {
    fireToolbarAppearanceChanged();
  }
}
