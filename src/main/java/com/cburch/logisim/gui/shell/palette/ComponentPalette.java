/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell.palette;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.gui.shell.FilterField;
import com.cburch.logisim.gui.shell.SectionPanel;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Spacing;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * The components a circuit can be built from, as a grid of tiles.
 *
 * <p>This replaces a folder tree of several hundred rows in which every component looked like
 * every other. A component is recognised by its symbol, so the symbol is what is shown; the groups
 * are named after what the components are for rather than after the part of the program they live
 * in; and the ones each person actually uses rise to the top on their own.
 */
public class ComponentPalette extends JPanel implements ProjectListener, LibraryListener {

  private static final long serialVersionUID = 1L;

  /** One component, with where it came from, ready to be placed in a group. */
  private record Item(Tool tool, String libraryId, String libraryName, String key) {}

  private final Project project;
  private final FilterField filter;
  private final ScrollableColumn groups = new ScrollableColumn();
  private final List<ComponentTile> tiles = new ArrayList<>();
  private final Map<String, Boolean> expanded = new LinkedHashMap<>();

  /** The key each component is remembered under, so choosing one does not rescan every library. */
  private final Map<Tool, String> keysByTool = new IdentityHashMap<>();

  private String filterText = "";
  private List<String> favourites;
  private List<String> recents;

  public ComponentPalette(Project project) {
    super(new BorderLayout());
    this.project = project;
    this.favourites = PaletteMemory.decode(AppPreferences.PALETTE_FAVOURITES.get());
    this.recents = PaletteMemory.decode(AppPreferences.PALETTE_RECENTS.get());

    filter =
        new FilterField(
            S.get("paletteFilterHint"),
            text -> {
              filterText = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
              rebuild();
            });
    filter.setKeyboardHandoff(this::chooseFirstVisible, direction -> focusFirstTile());

    final var top = new JPanel(new BorderLayout());
    top.setOpaque(false);
    top.setBorder(BorderFactory.createEmptyBorder(0, Spacing.sm(), Spacing.xs(), Spacing.sm()));
    top.add(filter, BorderLayout.CENTER);
    add(top, BorderLayout.NORTH);

    groups.setLayout(new BoxLayout(groups, BoxLayout.Y_AXIS));
    groups.setOpaque(false);
    final var scroll = new JScrollPane(groups);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    // How many tiles fit on a row decides how tall each group is, so a change of width has to be
    // laid out again rather than merely repainted.
    scroll.getViewport()
        .addComponentListener(
            new java.awt.event.ComponentAdapter() {
              @Override
              public void componentResized(java.awt.event.ComponentEvent event) {
                groups.revalidate();
              }
            });
    add(scroll, BorderLayout.CENTER);

    project.addProjectListener(this);
    project.addLibraryListener(this);
    Theme.addListener(this::rebuild);
    rebuild();
  }

  /** Every placeable component in the project, in the order its libraries declare them. */
  private List<Item> collect() {
    final var items = new ArrayList<Item>();
    final var file = project.getLogisimFile();
    if (file == null) return items;
    final Set<Library> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    collectFrom(file, items, visited, true);
    return items;
  }

  private void collectFrom(
      Library library, List<Item> items, Set<Library> visited, boolean projectRoot) {
    if ((!projectRoot && library.isHidden()) || !visited.add(library)) return;
    final var libraryId = library.getName();
    final var libraryName = displayNameOf(library);
    for (final var tool : library.getTools()) {
      if (!(tool instanceof AddTool addTool) || !isPlaceable(addTool)) continue;
      items.add(
          new Item(addTool, libraryId, libraryName, PaletteMemory.key(libraryId, tool.getName())));
    }
    for (final var child : library.getLibraries()) {
      collectFrom(child, items, visited, false);
    }
  }

  /** A circuit cannot be placed inside itself. */
  private boolean isPlaceable(AddTool tool) {
    return !(tool.getFactory(false) instanceof SubcircuitFactory subcircuit
        && subcircuit.getSubcircuit() == project.getCurrentCircuit());
  }

  private static String displayNameOf(Library library) {
    final var displayName = library.getDisplayName();
    if (displayName != null && !displayName.isBlank()) return displayName.trim();
    final var name = library.getName();
    return name == null ? "" : name.trim();
  }

  private boolean matches(Item item) {
    if (filterText.isEmpty()) return true;
    final var name = item.tool().getDisplayName();
    return name != null && name.toLowerCase(Locale.ROOT).contains(filterText);
  }

  /** Rebuilds every group. Cheap enough: the expensive part, drawing a symbol, is done lazily. */
  private void rebuild() {
    groups.removeAll();
    tiles.clear();

    final var items = collect();
    final var available = new java.util.HashSet<String>();
    final var byKey = new LinkedHashMap<String, Item>();
    keysByTool.clear();
    for (final var item : items) {
      available.add(item.key());
      byKey.putIfAbsent(item.key(), item);
      keysByTool.put(item.tool(), item.key());
    }
    favourites = PaletteMemory.retaining(favourites, available);
    recents = PaletteMemory.retaining(recents, available);

    if (filterText.isEmpty()) {
      addGroup(S.get("paletteGroupFavourites"), pick(byKey, favourites), AppIcons.Id.STAR);
      addGroup(S.get("paletteGroupRecent"), pick(byKey, recents), AppIcons.Id.HISTORY);
    }

    final var grouped = new LinkedHashMap<String, List<Item>>();
    // Declared first so the groups keep their order even when one of them turns out to be empty.
    for (final var group : PaletteCatalog.Group.values()) {
      grouped.put(group.title(), new ArrayList<>());
    }
    for (final var item : items) {
      if (!matches(item)) continue;
      final var heading =
          PaletteCatalog.groupOf(item.libraryId(), item.tool().getName())
              .map(PaletteCatalog.Group::title)
              .orElse(item.libraryName());
      grouped.computeIfAbsent(heading, key -> new ArrayList<>()).add(item);
    }
    for (final var group : grouped.entrySet()) {
      addGroup(group.getKey(), group.getValue(), null);
    }

    groups.add(Box.createVerticalGlue());
    setCurrentTool(project.getTool());
    revalidate();
    repaint();
  }

  private List<Item> pick(Map<String, Item> byKey, List<String> keys) {
    final var picked = new ArrayList<Item>(keys.size());
    for (final var key : keys) {
      final var item = byKey.get(key);
      if (item != null && matches(item)) picked.add(item);
    }
    return picked;
  }

  private void addGroup(String title, List<Item> items, AppIcons.Id icon) {
    if (items.isEmpty()) return;

    final var grid = new JPanel(new WrapLayout(FlowLayout.LEADING, Spacing.xs(), Spacing.xs()));
    grid.setOpaque(false);
    grid.setBorder(
        BorderFactory.createEmptyBorder(0, Spacing.sm(), Spacing.sm(), Spacing.sm()));
    for (final var item : items) {
      final var tile =
          new ComponentTile(item.tool(), this::choose, chosen -> showTileMenu(chosen, item.key()));
      tile.setFavourite(favourites.contains(item.key()));
      tiles.add(tile);
      grid.add(tile);
    }

    // A group the user folded away stays folded; a filter always opens what it matched.
    final var open = !filterText.isEmpty() || expanded.getOrDefault(title, Boolean.TRUE);
    final var section = new SectionPanel(title, grid, open);
    section.setAlignmentX(LEFT_ALIGNMENT);
    section.setBadge(icon);
    groups.add(section);
    section.addPropertyChangeListener(
        SectionPanel.EXPANDED_PROPERTY, event -> expanded.put(title, section.isExpanded()));
  }

  private void showTileMenu(ComponentTile tile, String key) {
    final var menu = new JPopupMenu();
    final var pinned = favourites.contains(key);
    final var item = new JMenuItem(S.get(pinned ? "paletteUnpin" : "palettePin"));
    item.addActionListener(
        event -> {
          favourites = PaletteMemory.toggled(favourites, key);
          AppPreferences.PALETTE_FAVOURITES.set(PaletteMemory.encode(favourites));
          rebuild();
        });
    menu.add(item);
    menu.show(tile, tile.getWidth() / 2, tile.getHeight() / 2);
  }

  /** Arms the first component the filter left, which is what Enter in a filter means. */
  private void chooseFirstVisible() {
    if (!tiles.isEmpty()) choose(tiles.get(0).tool());
  }

  /** Moves into the grid, where a tile answers Enter and Space itself. */
  private void focusFirstTile() {
    if (!tiles.isEmpty()) tiles.get(0).requestFocusInWindow();
  }

  /** Selects a component and remembers that it was used. */
  private void choose(Tool tool) {
    project.setTool(tool);
    final var key = keyOf(tool);
    if (key != null) {
      recents = PaletteMemory.withMostRecent(recents, key);
      AppPreferences.PALETTE_RECENTS.set(PaletteMemory.encode(recents));
    }
  }

  private String keyOf(Tool tool) {
    return keysByTool.get(tool);
  }

  private void setCurrentTool(Tool tool) {
    for (final var tile : tiles) {
      tile.setCurrent(tile.tool() == tool);
    }
  }

  /** Marks the component whose properties are showing, as the tree used to. */
  public void setHaloedTool(Tool tool) {
    setCurrentTool(tool == null ? project.getTool() : tool);
  }

  /** Rebuilds after a library has been added, removed or reloaded. */
  public void updateStructure() {
    rebuild();
  }

  @Override
  public void projectChanged(ProjectEvent event) {
    if (event.getAction() == ProjectEvent.ACTION_SET_TOOL) {
      setCurrentTool(project.getTool());
    } else if (event.getAction() == ProjectEvent.ACTION_SET_CURRENT) {
      rebuild();
    }
  }

  @Override
  public void libraryChanged(LibraryEvent event) {
    rebuild();
  }

  /**
   * The column of groups, which is always exactly as wide as the space it is scrolled in.
   *
   * <p>Without this the viewport sizes the column to the column's own preferred width. The wrapping
   * grid inside it would then be asked how tall it is at that width -- the width it asked for,
   * wide enough for every tile in a row -- and would answer with the height of a single row. Each
   * group would show its first few components and hide the rest.
   */
  private static class ScrollableColumn extends JPanel implements javax.swing.Scrollable {

    private static final long serialVersionUID = 1L;

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int up) {
      return 16;
    }

    @Override
    public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int up) {
      return orientation == javax.swing.SwingConstants.VERTICAL
          ? visible.height
          : visible.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  /**
   * A flow layout that wraps, and that reports the height the wrapping actually needs.
   *
   * <p>{@link FlowLayout} lays its children out in rows but always claims to need one row's
   * height, so inside a scroll pane it is given one row and the rest is clipped away.
   */
  private static class WrapLayout extends FlowLayout {

    private static final long serialVersionUID = 1L;

    WrapLayout(int align, int hgap, int vgap) {
      super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(java.awt.Container target) {
      return layoutSize(target);
    }

    @Override
    public Dimension minimumLayoutSize(java.awt.Container target) {
      final var size = layoutSize(target);
      size.width -= getHgap() + 1;
      return size;
    }

    private Dimension layoutSize(java.awt.Container target) {
      synchronized (target.getTreeLock()) {
        // The first time round the grid has no width of its own yet, and asking for a preferred
        // size while pretending it is unlimited yields the height of a single row -- which is
        // then all the space it is given, so every row but the first is clipped away. The width
        // it will actually have is the nearest ancestor's, so use that instead.
        var container = (java.awt.Container) target;
        while (container.getWidth() == 0 && container.getParent() != null) {
          container = container.getParent();
        }
        var available = container.getWidth();
        if (available == 0) available = Integer.MAX_VALUE;

        final var insets = target.getInsets();
        final var horizontalInsets = insets.left + insets.right + getHgap() * 2;
        final var maxWidth = Math.max(1, available - horizontalInsets);

        final var size = new Dimension(0, 0);
        var rowWidth = 0;
        var rowHeight = 0;
        for (final var child : visibleChildren(target)) {
          final var childSize = child.getPreferredSize();
          if (rowWidth + childSize.width > maxWidth && rowWidth > 0) {
            size.width = Math.max(size.width, rowWidth);
            size.height += rowHeight + getVgap();
            rowWidth = 0;
            rowHeight = 0;
          }
          if (rowWidth > 0) rowWidth += getHgap();
          rowWidth += childSize.width;
          rowHeight = Math.max(rowHeight, childSize.height);
        }
        size.width = Math.max(size.width, rowWidth) + horizontalInsets;
        size.height += rowHeight + insets.top + insets.bottom + getVgap() * 2;
        return size;
      }
    }

    private static List<Component> visibleChildren(java.awt.Container target) {
      final var children = new ArrayList<Component>(target.getComponentCount());
      for (var i = 0; i < target.getComponentCount(); i++) {
        final var child = target.getComponent(i);
        if (child.isVisible()) children.add(child);
      }
      return children;
    }
  }

  /** The panel itself, for the shell to place. */
  public JComponent component() {
    return this;
  }
}
