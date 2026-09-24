/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Which circuits are open in the editor area, and which one is showing.
 *
 * <p>A project holds many circuits but has always had one canvas: choosing a circuit in the tree
 * replaced what was on screen, and getting back to the previous one meant finding it in the tree
 * again. The tabs are a record of what you have been working on, laid over that one canvas — they
 * do not each own an editor, they are names for places to go.
 *
 * <p>This class is deliberately free of Swing and of the project classes: it is the part with the
 * awkward rules, so it is the part worth testing on its own. The window supplies a {@link
 * Navigator} that knows how to actually show a tab's target.
 */
public final class EditorTabModel {

  /** What is being edited: the wiring of a circuit, its drawn appearance, or HDL source. */
  public enum Kind {
    LAYOUT,
    APPEARANCE,
    HDL
  }

  /**
   * One tab.
   *
   * @param kind which editor the tab opens
   * @param target the circuit or HDL model being edited; compared by identity
   */
  public record Tab(Kind kind, Object target) {}

  /** Shows a tab's target. Called only for a change the user made here. */
  @FunctionalInterface
  public interface Navigator {
    void activate(Tab tab);
  }

  private final List<Tab> tabs = new ArrayList<>();
  private final Set<Object> dirtyTargets = Collections.newSetFromMap(new IdentityHashMap<>());
  private int selectedIndex = -1;

  private Navigator navigator = tab -> {};
  private Consumer<EditorTabModel> listener = model -> {};
  private Runnable emptyListener = () -> {};

  /**
   * Guards against the loop where showing a tab changes the project, the project change comes
   * back here, and this class then asks for the tab to be shown again.
   */
  private boolean navigating;

  public void setNavigator(Navigator navigator) {
    this.navigator = navigator == null ? tab -> {} : navigator;
  }

  /** Called after any change, so the tab strip can redraw itself. */
  public void setListener(Consumer<EditorTabModel> listener) {
    this.listener = listener == null ? model -> {} : listener;
  }

  /** Hides the live editor when the last tab is closed; project/circuit state is retained. */
  public void setEmptyListener(Runnable listener) {
    emptyListener = listener == null ? () -> {} : listener;
  }

  public List<Tab> tabs() {
    return List.copyOf(tabs);
  }

  public int count() {
    return tabs.size();
  }

  public int selectedIndex() {
    return selectedIndex;
  }

  public Tab selected() {
    return selectedIndex < 0 ? null : tabs.get(selectedIndex);
  }

  /** The position of {@code tab}, or -1. Targets are matched by identity, not by name. */
  public int indexOf(Tab tab) {
    for (var index = 0; index < tabs.size(); index++) {
      final var candidate = tabs.get(index);
      if (candidate.kind() == tab.kind() && candidate.target() == tab.target()) return index;
    }
    return -1;
  }

  /**
   * Makes {@code tab} the one showing, opening it if it is not already, and tells the window to
   * show it. Use this for a request coming from somewhere other than the tab strip.
   */
  public void open(Tab tab) {
    final var index = ensure(tab);
    select(index);
  }

  /**
   * Records that the project is now showing {@code tab}, without asking for it to be shown again.
   *
   * <p>This is the direction that matters for correctness: the project can change from the
   * explorer, a menu, or a double-click on a subcircuit, and the tabs have to follow without
   * bouncing the change back.
   */
  public void syncTo(Tab tab) {
    navigating = true;
    try {
      select(ensure(tab));
    } finally {
      navigating = false;
    }
  }

  /** Selects the tab at {@code index} and asks the window to show it. */
  public void select(int index) {
    if (index < 0 || index >= tabs.size() || index == selectedIndex) {
      if (index == selectedIndex) changed();
      return;
    }
    selectedIndex = index;
    if (!navigating) {
      navigating = true;
      try {
        navigator.activate(tabs.get(index));
      } finally {
        navigating = false;
      }
    }
    changed();
  }

  /**
   * Closes the tab at {@code index}.
   *
   * <p>Only the tab goes: the circuit stays in the project, because a tab is a place the user has
   * been rather than a thing they own. Closing the tab that is showing moves to its neighbour,
   * preferring the one that takes its place.
   */
  public void close(int index) {
    if (index < 0 || index >= tabs.size()) return;
    final var wasSelected = index == selectedIndex;
    tabs.remove(index);
    if (tabs.isEmpty()) {
      selectedIndex = -1;
      changed();
      emptyListener.run();
      return;
    }
    if (wasSelected) {
      final var next = Math.min(index, tabs.size() - 1);
      selectedIndex = -1;
      select(next);
      return;
    }
    if (index < selectedIndex) selectedIndex--;
    changed();
  }

  /** Closes every tab showing {@code target}, for a circuit that has been deleted. */
  public void removeTarget(Object target) {
    final var selected = selected();
    final var previousIndex = selectedIndex;
    // Remove all views before navigating, so deleting a circuit cannot activate its appearance.
    tabs.removeIf(tab -> tab.target() == target);
    dirtyTargets.remove(target);
    if (selected != null && selected.target() != target) {
      selectedIndex = indexOf(selected);
      changed();
    } else {
      selectedIndex = -1;
      if (tabs.isEmpty()) {
        changed();
        if (selected != null) emptyListener.run();
      } else {
        select(Math.min(Math.max(0, previousIndex), tabs.size() - 1));
      }
    }
  }

  /** Marks or unmarks {@code target} as having unsaved changes. */
  public void setDirty(Object target, boolean dirty) {
    if (target == null) return;
    final var changed = dirty ? dirtyTargets.add(target) : dirtyTargets.remove(target);
    if (changed) changed();
  }

  /** Clears every unsaved marker, for a project that has just been saved. */
  public void clearDirty() {
    if (dirtyTargets.isEmpty()) return;
    dirtyTargets.clear();
    changed();
  }

  public boolean isDirty(Tab tab) {
    return tab != null && dirtyTargets.contains(tab.target());
  }

  /** Drops every tab, for a window that is loading a different project. */
  public void clear() {
    tabs.clear();
    dirtyTargets.clear();
    selectedIndex = -1;
    changed();
  }

  /** Adds {@code tab} if it is not open, and returns where it is. */
  private int ensure(Tab tab) {
    final var existing = indexOf(tab);
    if (existing >= 0) return existing;
    tabs.add(tab);
    changed();
    return tabs.size() - 1;
  }

  private void changed() {
    listener.accept(this);
  }
}
