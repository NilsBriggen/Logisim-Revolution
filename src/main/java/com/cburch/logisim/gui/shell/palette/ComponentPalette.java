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
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

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
  private final JTextArea fullName = new JTextArea();
  private final JScrollPane scroll = new JScrollPane(groups);
  private final Runnable themeListener = this::rebuild;
  private final Consumer<List<String>> saveFavourites;
  private final Consumer<List<String>> saveRecents;
  private boolean listening;
  private boolean disposed;
  private ComponentTile leadTile;

  /** The key each component is remembered under, so choosing one does not rescan every library. */
  private final Map<Tool, String> keysByTool = new IdentityHashMap<>();

  private String filterText = "";
  private List<String> favourites;
  private List<String> recents;

  public ComponentPalette(Project project) {
    this(project, PaletteMemory.decode(AppPreferences.PALETTE_FAVOURITES.get()),
        PaletteMemory.decode(AppPreferences.PALETTE_RECENTS.get()),
        values -> AppPreferences.PALETTE_FAVOURITES.set(PaletteMemory.encode(values)),
        values -> AppPreferences.PALETTE_RECENTS.set(PaletteMemory.encode(values)));
  }

  ComponentPalette(Project project, List<String> favourites, List<String> recents,
      Consumer<List<String>> saveFavourites, Consumer<List<String>> saveRecents) {
    super(new BorderLayout());
    this.project = project;
    this.favourites = List.copyOf(favourites);
    this.recents = List.copyOf(recents);
    this.saveFavourites = saveFavourites;
    this.saveRecents = saveRecents;

    filter =
        new FilterField(
            S.get("paletteFilterHint"),
            this::applyFilter);
    filter.setKeyboardHandoff(this::chooseFirstVisible, this::focusFirstTile);

    final var top = new JPanel(new BorderLayout());
    top.setOpaque(false);
    top.setBorder(BorderFactory.createEmptyBorder(0, Spacing.sm(), Spacing.xs(), Spacing.sm()));
    top.add(filter, BorderLayout.CENTER);
    fullName.setEditable(false);
    fullName.setFocusable(false);
    fullName.setLineWrap(true);
    fullName.setWrapStyleWord(true);
    fullName.setOpaque(false);
    fullName.setRows(1);
    fullName.setVisible(false);
    fullName.setFont(UiFonts.small());
    top.add(fullName, BorderLayout.SOUTH);
    add(top, BorderLayout.NORTH);

    groups.setLayout(new BoxLayout(groups, BoxLayout.Y_AXIS));
    groups.setOpaque(false);
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

    rebuild();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (!listening && !disposed) {
      project.addProjectListener(this);
      project.addLibraryListener(this);
      Theme.addListener(themeListener);
      listening = true;
      rebuild();
    }
  }

  @Override
  public void removeNotify() {
    unsubscribe();
    super.removeNotify();
  }

  /** Releases subscriptions even when an owner disposes the palette before removing its peers. */
  public void dispose() {
    disposed = true;
    unsubscribe();
  }

  private void unsubscribe() {
    if (!listening) return;
    Theme.removeListener(themeListener);
    project.removeProjectListener(this);
    project.removeLibraryListener(this);
    listening = false;
  }

  private void applyFilter(String text) {
    final var query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    if (query.equals(filterText) || disposed) return;
    filterText = query;
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
    return score(item) >= 0;
  }

  private int score(Item item) {
    final var score =
        PaletteSearch.score(item.tool().getName(), item.tool().getDisplayName(), filterText);
    if (score >= 0) return score;
    final var category = PaletteCatalog.groupOf(item.libraryId(), item.tool().getName())
        .map(PaletteCatalog.Group::title).orElse(item.libraryName());
    return category.toLowerCase(Locale.ROOT).contains(filterText)
        || item.libraryName().toLowerCase(Locale.ROOT).contains(filterText) ? 5 : -1;
  }

  /** Rebuilds every group. Cheap enough: the expensive part, drawing a symbol, is done lazily. */
  private void rebuild() {
    if (disposed) return;
    final var focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    final var focusedTile = focus instanceof ComponentTile tile && tiles.contains(tile) ? tile : null;
    final var rememberedKey = leadTile == null ? null : keysByTool.get(leadTile.tool());
    final var focusedGroup = focusedTile == null ? null : focusedTile.getClientProperty("palette.group");
    final var position = scroll.getViewport().getViewPosition();
    groups.removeAll();
    tiles.clear();
    leadTile = null;
    fullName.setFont(UiFonts.small());
    fullName.setText("");

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

    if (!filterText.isEmpty()) {
      final var results = items.stream().filter(this::matches)
          .sorted(Comparator.comparingInt(this::score)).toList();
      addGroup("results", S.get("paletteGroupResults"), results, null);
      if (results.isEmpty()) {
        final var message = new JTextArea(S.get("paletteNoResults", filter.getText()));
        message.setEditable(false);
        message.setFocusable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setOpaque(false);
        message.setBorder(Spacing.innerBorder());
        groups.add(message);
        final var clear = new JButton(S.get("paletteClearSearch"));
        clear.addActionListener(event -> {
          filter.clear();
          filter.requestFocusInWindow();
        });
        groups.add(clear);
      }
    } else {
      addGroup("favourites", S.get("paletteGroupFavourites"), pick(byKey, favourites), AppIcons.Id.STAR);
      addGroup("recents", S.get("paletteGroupRecent"), pick(byKey, recents), AppIcons.Id.HISTORY);
      final var grouped = new LinkedHashMap<String, List<Item>>();
      final var titles = new LinkedHashMap<String, String>();
      // Project components precede built-ins and retain their own file/library name.
      final var projectId = project.getLogisimFile() == null ? "" : project.getLogisimFile().getName();
      grouped.put("library:" + projectId, new ArrayList<>());
      for (final var group : PaletteCatalog.Group.values()) {
        grouped.put(group.name(), new ArrayList<>());
        titles.put(group.name(), group.title());
      }
      for (final var item : items) {
        final var group = item.libraryId().equals(projectId)
            ? java.util.Optional.<PaletteCatalog.Group>empty()
            : PaletteCatalog.groupOf(item.libraryId(), item.tool().getName());
        final var id = group.map(Enum::name).orElse("library:" + item.libraryId());
        titles.putIfAbsent(id, item.libraryName());
        grouped.computeIfAbsent(id, key -> new ArrayList<>()).add(item);
      }
      for (final var group : grouped.entrySet()) {
        addGroup(group.getKey(), titles.get(group.getKey()), group.getValue(), null);
      }
    }

    groups.add(Box.createVerticalGlue());
    setCurrentTool(project.getTool());
    final var visible = visibleTiles();
    final var remembered = visible.stream()
        .filter(tile -> java.util.Objects.equals(rememberedKey, keysByTool.get(tile.tool())))
        .filter(tile -> focusedGroup == null
            || focusedGroup.equals(tile.getClientProperty("palette.group")))
        .findFirst().orElse(visible.isEmpty() ? null : visible.get(0));
    setLead(remembered, focusedTile != null);
    revalidate();
    repaint();
    if (focusedTile == null) scroll.getViewport().setViewPosition(position);
  }

  private List<Item> pick(Map<String, Item> byKey, List<String> keys) {
    final var picked = new ArrayList<Item>(keys.size());
    for (final var key : keys) {
      final var item = byKey.get(key);
      if (item != null && matches(item)) picked.add(item);
    }
    return picked;
  }

  private void addGroup(String id, String title, List<Item> items, AppIcons.Id icon) {
    if (items.isEmpty()) return;

    final var grid = new JPanel(new TileGridLayout());
    grid.setOpaque(false);
    grid.setBorder(
        BorderFactory.createEmptyBorder(0, Spacing.sm(), Spacing.sm(), Spacing.sm()));
    for (final var item : items) {
      final var tile =
          new ComponentTile(item.tool(), this::choose, chosen -> showTileMenu(chosen, item.key()));
      tile.setFavourite(favourites.contains(item.key()));
      tile.setFocusable(false);
      tile.putClientProperty("palette.group", id);
      tile.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent event) {
          setLead(tile, false);
        }
      });
      tile.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent event) {
          if (navigate(tile, event.getKeyCode())) event.consume();
        }
      });
      tiles.add(tile);
      grid.add(tile);
    }

    // A group the user folded away stays folded; a filter always opens what it matched.
    final var open = !filterText.isEmpty() || expanded.getOrDefault(id, Boolean.TRUE);
    final var section = new PaletteSection(title, grid, open, icon);
    section.setAlignmentX(LEFT_ALIGNMENT);
    groups.add(section);
    section.addPropertyChangeListener(
        "expanded", event -> {
          expanded.put(id, section.open);
          final var visible = visibleTiles();
          if (!visible.contains(leadTile)) setLead(visible.isEmpty() ? null : visible.get(0), false);
        });
  }

  private void showTileMenu(ComponentTile tile, String key) {
    final var menu = new JPopupMenu();
    final var pinned = favourites.contains(key);
    final var item = new JMenuItem(S.get(pinned ? "paletteUnpin" : "palettePin"));
    item.addActionListener(
        event -> {
          favourites = PaletteMemory.toggled(favourites, key);
          saveFavourites.accept(favourites);
          rebuild();
          final var target = tiles.stream()
              .filter(candidate -> key.equals(keyOf(candidate.tool())))
              .findFirst().orElse(null);
          setLead(target, true);
        });
    menu.add(item);
    menu.show(tile, tile.getWidth() / 2, tile.getHeight() / 2);
  }

  /** Arms the first component the filter left, which is what Enter in a filter means. */
  private void chooseFirstVisible() {
    final var visible = visibleTiles();
    if (!visible.isEmpty()) choose(visible.get(0).tool());
  }

  /** Moves into the grid, where a tile answers Enter and Space itself. */
  private void focusFirstTile(int direction) {
    final var visible = visibleTiles();
    if (!visible.isEmpty()) {
      setLead(visible.get(direction < 0 ? visible.size() - 1 : 0), true);
    }
  }

  /** Selects a component and remembers that it was used. */
  private void choose(Tool tool) {
    project.setTool(tool);
    final var key = keyOf(tool);
    if (key != null) {
      recents = PaletteMemory.withMostRecent(recents, key);
      saveRecents.accept(recents);
      rebuild();
    }
  }

  private String keyOf(Tool tool) {
    return keysByTool.get(tool);
  }

  private List<ComponentTile> visibleTiles() {
    return tiles.stream().filter(tile -> tile.getParent().isVisible()).toList();
  }

  private void setLead(ComponentTile tile, boolean focus) {
    leadTile = tile;
    for (final var candidate : tiles) candidate.setFocusable(candidate == tile);
    fullName.setText(tile == null ? "" : tile.getAccessibleContext().getAccessibleName());
    fullName.setVisible(tile != null && (focus || tile.isFocusOwner()));
    if (tile != null && focus) {
      tile.requestFocusInWindow();
      tile.scrollRectToVisible(new Rectangle(0, 0, tile.getWidth(), tile.getHeight()));
    }
  }

  private boolean navigate(ComponentTile tile, int key) {
    final var visible = visibleTiles();
    final var index = visible.indexOf(tile);
    if (index < 0) return false;
    if (key == KeyEvent.VK_ESCAPE) {
      if (!filter.getText().isEmpty()) {
        filter.clear();
        filter.requestFocusInWindow();
      } else if (project.getFrame() != null) {
        project.getFrame().getCanvas().requestFocusInWindow();
      } else {
        filter.requestFocusInWindow();
      }
      return true;
    }
    final var bounds = visible.stream()
        .map(candidate -> SwingUtilities.convertRectangle(
            candidate.getParent(), candidate.getBounds(), groups)).toList();
    final var target = navigationTarget(bounds, index, key);
    if (target < 0) return false;
    setLead(visible.get(target), true);
    return true;
  }

  /** Uses actual rows, including changes of column count between sections. */
  static int navigationTarget(List<Rectangle> bounds, int index, int key) {
    if (bounds.isEmpty()) return -1;
    if (key == KeyEvent.VK_HOME) return 0;
    if (key == KeyEvent.VK_END) return bounds.size() - 1;
    if (key == KeyEvent.VK_LEFT) return Math.max(0, index - 1);
    if (key == KeyEvent.VK_RIGHT) return Math.min(bounds.size() - 1, index + 1);
    if (key != KeyEvent.VK_UP && key != KeyEvent.VK_DOWN) return -1;
    final var from = bounds.get(index);
    var best = index;
    var distance = Double.POSITIVE_INFINITY;
    for (var i = 0; i < bounds.size(); i++) {
      final var to = bounds.get(i);
      final var dy = to.getCenterY() - from.getCenterY();
      if (key == KeyEvent.VK_UP ? dy >= 0 : dy <= 0) continue;
      final var score = Math.abs(dy) * 10000 + Math.abs(to.getCenterX() - from.getCenterX());
      if (score < distance) {
        best = i;
        distance = score;
      }
    }
    return best;
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

  /** A normal focusable button is also the section's accessible expand/collapse control. */
  private static class PaletteSection extends JPanel {
    private static final long serialVersionUID = 1L;
    private boolean open;

    PaletteSection(String title, JComponent content, boolean open, AppIcons.Id badge) {
      super(new BorderLayout());
      this.open = open;
      setOpaque(false);
      final var header = new JButton(title);
      header.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
      header.setFont(UiFonts.small());
      header.setForeground(Tokens.sidePanelHeaderForeground());
      header.setBorder(Spacing.border(Spacing.XS, Spacing.SM, Spacing.XS, Spacing.SM));
      header.setContentAreaFilled(false);
      final Runnable refresh = () -> {
        header.setIcon(AppIcons.colored(this.open ? AppIcons.Id.CHEVRON_DOWN
            : AppIcons.Id.CHEVRON_RIGHT, 12, Tokens.sidePanelHeaderForeground()));
        header.getAccessibleContext().setAccessibleDescription(title);
        content.setVisible(this.open);
      };
      header.setSelected(open);
      header.addActionListener(event -> {
        final var previous = this.open;
        this.open = !this.open;
        header.setSelected(this.open);
        refresh.run();
        revalidate();
        firePropertyChange("expanded", previous, this.open);
      });
      final var row = new JPanel(new BorderLayout());
      row.setOpaque(false);
      row.add(header, BorderLayout.CENTER);
      if (badge != null) {
        final var icon = new javax.swing.JLabel(AppIcons.colored(badge, 12, Tokens.mutedForeground()));
        icon.setBorder(Spacing.border(0, 0, 0, Spacing.SM));
        row.add(icon, BorderLayout.EAST);
      }
      add(row, BorderLayout.NORTH);
      add(content, BorderLayout.CENTER);
      refresh.run();
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
  }

  /** The same measured geometry drives both preferred height and actual placement. */
  static class TileGridLayout implements LayoutManager {
    private record Geometry(int columns, int width, int height, int rows) {}

    @Override
    public void addLayoutComponent(String name, Component component) {}

    @Override
    public void removeLayoutComponent(Component component) {}

    private Geometry measure(Container target) {
      var container = target;
      while (container.getWidth() == 0 && container.getParent() != null) {
        container = container.getParent();
      }
      // BoxLayout asks for the new height before assigning the grid its new width. The
      // viewport already knows that width, while the grid may still have yesterday's bounds.
      for (var ancestor = target.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
        if (ancestor instanceof javax.swing.JViewport viewport) {
          container = viewport;
          break;
        }
      }
      var preferredWidth = 1;
      var height = 1;
      for (final var child : target.getComponents()) {
        preferredWidth = Math.max(preferredWidth, child.getPreferredSize().width);
        height = Math.max(height, child.getPreferredSize().height);
      }
      final var insets = target.getInsets();
      final var available = Math.max(1, (container.getWidth() == 0
          ? preferredWidth + insets.left + insets.right : container.getWidth())
          - insets.left - insets.right);
      final var gap = Spacing.xs();
      final var columns = Math.max(1, (available + gap) / (preferredWidth + gap));
      return new Geometry(columns, Math.max(1, (available - (columns - 1) * gap) / columns),
          height, (target.getComponentCount() + columns - 1) / columns);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
      final var geometry = measure(target);
      final var insets = target.getInsets();
      var contentHeight = geometry.rows * geometry.height;
      if (geometry.columns == 1) {
        contentHeight = 0;
        for (final var child : target.getComponents()) {
          contentHeight += child instanceof ComponentTile tile
              ? tile.compactHeight(geometry.width) : child.getPreferredSize().height;
        }
      }
      return new Dimension(geometry.columns * geometry.width
          + (geometry.columns - 1) * Spacing.xs() + insets.left + insets.right,
          contentHeight + Math.max(0, geometry.rows - 1) * Spacing.xs()
              + insets.top + insets.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
      final var preferred = preferredLayoutSize(target);
      return new Dimension(1, preferred.height);
    }

    @Override
    public void layoutContainer(Container target) {
      final var geometry = measure(target);
      final var insets = target.getInsets();
      var rowY = insets.top;
      for (var i = 0; i < target.getComponentCount(); i++) {
        final var child = target.getComponent(i);
        final var compact = geometry.columns == 1;
        if (child instanceof ComponentTile tile) tile.setCompact(compact);
        final var height = compact && child instanceof ComponentTile tile
            ? tile.compactHeight(geometry.width) : geometry.height;
        final var column = target.getComponentOrientation().isLeftToRight()
            ? i % geometry.columns : geometry.columns - 1 - i % geometry.columns;
        child.setBounds(
            insets.left + column * (geometry.width + Spacing.xs()),
            compact ? rowY : insets.top + i / geometry.columns * (geometry.height + Spacing.xs()),
            geometry.width, height);
        if (compact) rowY += height + Spacing.xs();
      }
    }
  }

  /** The panel itself, for the shell to place. */
  public JComponent component() {
    return this;
  }
}
