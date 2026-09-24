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

import com.cburch.draw.toolbar.Toolbar;
import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.generated.BuildInfo;
import com.cburch.logisim.gui.appear.AppearanceView;
import com.cburch.logisim.gui.generic.AttrTable;
import com.cburch.logisim.gui.generic.AttrTableModel;
import com.cburch.logisim.gui.generic.BasicZoomModel;
import com.cburch.logisim.gui.generic.CanvasPane;
import com.cburch.logisim.gui.generic.CardPanel;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.generic.RegTabContent;
import com.cburch.logisim.gui.generic.ZoomControl;
import com.cburch.logisim.gui.generic.ZoomModel;
import com.cburch.logisim.gui.menu.MainMenuListener;
import com.cburch.logisim.gui.search.OmniSearchDialog;
import com.cburch.logisim.gui.shell.ActivityBar;
import com.cburch.logisim.gui.shell.BottomPanel;
import com.cburch.logisim.gui.shell.CircuitListView;
import com.cburch.logisim.gui.shell.EditorArea;
import com.cburch.logisim.gui.shell.EditorTabModel;
import com.cburch.logisim.gui.shell.EditorTabs;
import com.cburch.logisim.gui.shell.Inspector;
import com.cburch.logisim.gui.shell.LayoutPrefs;
import com.cburch.logisim.gui.shell.MainToolbar;
import com.cburch.logisim.gui.shell.SectionPanel;
import com.cburch.logisim.gui.shell.ShellLayout;
import com.cburch.logisim.gui.shell.SidePanel;
import com.cburch.logisim.gui.shell.SideView;
import com.cburch.logisim.gui.shell.StatusBar;
import com.cburch.logisim.gui.shell.WelcomePanel;
import com.cburch.logisim.gui.shell.ZoomPill;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.prefs.PreferencesFrame;
import com.cburch.logisim.prefs.AppPreferences;
import com.formdev.flatlaf.FlatClientProperties;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.proj.ProjectCloseConfirmation;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.JFileChoosers;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.vhdl.base.HdlModel;
import com.cburch.logisim.vhdl.base.VhdlContent;
import com.cburch.logisim.vhdl.gui.HdlContentView;
import com.cburch.logisim.vhdl.gui.VhdlSimState;
import com.cburch.logisim.vhdl.gui.VhdlSimulatorConsole;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class Frame extends LFrame.MainWindow implements LocaleListener {
  private static final long serialVersionUID = 1L;

  public static final String EDITOR_VIEW = "editorView";
  public static final String EXPLORER_VIEW = "explorerView";
  public static final String EDIT_LAYOUT = "layout";
  public static final String EDIT_APPEARANCE = "appearance";
  public static final String EDIT_HDL = "hdl";
  /** The card shown when the application is started without a file. */
  public static final String EDIT_WELCOME = "welcome";
  /** Identifier of the side view listing the project's circuits and libraries. */
  public static final String EXPLORER_SIDE_VIEW = "explorer";
  /** Identifier of the side view holding the component libraries. */
  public static final String LIBRARY_SIDE_VIEW = "library";
  /** Identifier of the side view showing the running simulation. */
  public static final String SIMULATION_SIDE_VIEW = "simulation";
  /** Identifier of the VHDL console in the drawer below the canvas. */
  private static final String VHDL_BOTTOM_PANEL = "vhdlConsole";
  private static final String LOG_BOTTOM_PANEL = "log";
  private static final String TEST_BOTTOM_PANEL = "test";
  private static final String EDIT_EMPTY = "empty";
  private final Timer timer = new Timer();
  private final Project project;
  private final MyProjectListener myProjectListener = new MyProjectListener();
  // GUI elements shared between views
  private final MainMenuListener menuListener;
  private final Toolbar toolbar;
  private final Toolbar simulationToolbar;
  private final KeyboardToolSelection.Registration keyboardToolSelection;
  private final ShellLayout shell;
  private final EditorTabModel editorTabModel = new EditorTabModel();
  private final EditorTabs editorTabs;
  private final SidePanel sidePanel;
  private final Inspector inspector;
  private final BottomPanel bottomPanel;
  private final StatusBar statusBar;
  private final SectionPanel stateSection;
  private final ZoomPill zoomPill;
  private final WelcomePanel welcomePanel;
  private final EditorArea editorArea;

  /** Whether the welcome screen is covering the canvas. */
  private boolean welcomeShowing;
  private boolean editorClosed;
  private final javax.swing.JLabel emptyEditorHint = new javax.swing.JLabel();
  private boolean closeResourcesReleased;

  /** The editor the project asked for while the welcome screen was up. */
  private String pendingEditorView;
  private final JPanel mainPanelSuper;
  private final CardPanel mainPanel;
  // left-side elements
  private final Toolbox toolbox;
  private final ToolboxToolbarModel circuitActions;
  private final CircuitListView circuitList;
  private final SimulationExplorer simExplorer;
  private final AttrTable attrTable;
  private final ZoomControl zoom;
  // for the Layout view
  private final LayoutToolbarModel layoutToolbarModel;
  private final Canvas layoutCanvas;
  private final CanvasPane layoutCanvasPane;
  private final CircuitViewMemory layoutViewMemory = new CircuitViewMemory();
  private final VhdlSimulatorConsole vhdlSimulatorConsole;
  private final HdlContentView hdlEditor;
  private final ZoomModel layoutZoomModel;
  private final LayoutEditHandler layoutEditHandler;
  private final AttrTableSelectionModel attrTableSelectionModel;
  // for the Appearance view
  private AppearanceView appearance;
  private final RegTabContent regTabContent;

  /**
   * A side view that simply shows a panel the window already builds.
   *
   * <p>The panels themselves are unchanged here; what changed is where they live and that only
   * one is on screen at a time.
   */
  private static final class SimpleSideView implements SideView {
    private final String id;
    private final String titleKey;
    private final AppIcons.Id icon;
    private final JComponent component;
    private final JComponent headerActions;

    SimpleSideView(String id, String titleKey, AppIcons.Id icon, JComponent component) {
      this(id, titleKey, icon, component, null);
    }

    SimpleSideView(
        String id,
        String titleKey,
        AppIcons.Id icon,
        JComponent component,
        JComponent headerActions) {
      this.id = id;
      this.titleKey = titleKey;
      this.icon = icon;
      this.component = component;
      this.headerActions = headerActions;
    }

    @Override
    public JComponent headerActions() {
      return headerActions;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String title() {
      return S.get(titleKey);
    }

    @Override
    public AppIcons.Id icon() {
      return icon;
    }

    @Override
    public JComponent component() {
      return component;
    }
  }

  public Frame(Project project) {
    super(project);
    this.project = project;

    setBackground(Color.white);
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(new MyWindowListener());

    project.addProjectListener(myProjectListener);
    project.addLibraryListener(myProjectListener);
    project.addCircuitListener(myProjectListener);

    // set up elements for the Layout view
    layoutToolbarModel = new LayoutToolbarModel(project);
    layoutCanvas = new Canvas(project);
    layoutCanvasPane = new CanvasPane(layoutCanvas);

    layoutZoomModel =
        new BasicZoomModel(
            AppPreferences.LAYOUT_SHOW_GRID,
            AppPreferences.LAYOUT_ZOOM,
            buildZoomSteps(),
            layoutCanvasPane);

    layoutCanvas.getGridPainter().setZoomModel(layoutZoomModel);
    layoutEditHandler = new LayoutEditHandler(this);
    attrTableSelectionModel = new AttrTableSelectionModel(project, this);

    // set up menu bar and toolbar
    menuListener = new MainMenuListener(this, menubar);
    menuListener.setEditHandler(layoutEditHandler);
    toolbar = new Toolbar(layoutToolbarModel);

    // set up the side views
    toolbox = new Toolbox(project);
    circuitActions = new ToolboxToolbarModel(this, menuListener);
    simExplorer = new SimulationExplorer(project, menuListener);
    attrTable = new AttrTable(this);
    regTabContent = new RegTabContent(this);
    zoom = new ZoomControl(layoutZoomModel, layoutCanvas);

    // set up the editor area: one canvas, with tabs above it choosing what it shows
    mainPanelSuper = new JPanel(new BorderLayout());
    // The canvas fills the editor area, so a focus ring around it marks nothing useful and only
    // draws a blue rectangle across the middle of the window.
    layoutCanvasPane.setBorder(null);
    layoutCanvasPane.setZoomModel(layoutZoomModel);
    mainPanel = new CardPanel();
    mainPanel.addView(EDIT_LAYOUT, layoutCanvasPane);
    mainPanel.setView(EDIT_LAYOUT);
    mainPanelSuper.add(mainPanel, BorderLayout.CENTER);

    hdlEditor = new HdlContentView(project);
    mainPanel.addView(EDIT_HDL, hdlEditor);
    vhdlSimulatorConsole = new VhdlSimulatorConsole(project);

    final var state = new VhdlSimState(project);
    state.stateChanged();
    project.getVhdlSimulator().addVhdlSimStateListener(state);

    welcomePanel =
        new WelcomePanel(
            () -> ProjectActions.doNew(project),
            () -> ProjectActions.doOpen(this, project),
            file -> ProjectActions.doOpenReplacingBlank(this, project, project, file));
    mainPanel.addView(EDIT_WELCOME, welcomePanel);
    final var emptyEditor = new JPanel(new java.awt.GridBagLayout());
    emptyEditor.setFocusable(true);
    emptyEditor.add(emptyEditorHint);
    mainPanel.addView(EDIT_EMPTY, emptyEditor);

    editorTabModel.setNavigator(this::activateEditorTab);
    editorTabs = new EditorTabs(editorTabModel, this::editorTabTitle);
    editorArea = new EditorArea(editorTabs, mainPanelSuper);
    zoomPill = new ZoomPill(zoom, layoutZoomModel);
    editorArea.setOverlay(zoomPill);

    circuitList = new CircuitListView(project);
    sidePanel = new SidePanel();
    sidePanel.addView(
        new SimpleSideView(
            EXPLORER_SIDE_VIEW,
            "explorerTab",
            AppIcons.Id.EXPLORER,
            circuitList,
            new Toolbar(circuitActions)));
    sidePanel.addView(
        new SimpleSideView(
            LIBRARY_SIDE_VIEW,
            "libraryTab",
            AppIcons.Id.LIBRARY,
            toolbox,
            toolbox.headerActions()));
    sidePanel.addView(
        new SimpleSideView(SIMULATION_SIDE_VIEW, "simulateTab", AppIcons.Id.SIMULATION,
            simExplorer));

    final var activityBar = new ActivityBar();
    for (final var view : sidePanel.views()) {
      activityBar.addView(view, view.title(), this::toggleSideView);
    }
    activityBar.addAction(
        "search",
        AppIcons.Id.SEARCH,
        S.get("searchTitle"),
        () -> OmniSearchDialog.showForWindow(this));
    activityBar.addAction(
        "settings",
        AppIcons.Id.SETTINGS,
        S.get("preferencesFrameMenuItem"),
        PreferencesFrame::showPreferences);

    // The panel's heading names what is selected, so the table does not repeat it above itself.
    attrTable.setTitleEnabled(false);
    stateSection = new SectionPanel(S.get("stateTab"), regTabContent, false);
    final var inspectorBody = new JPanel(new BorderLayout());
    inspectorBody.add(attrTable, BorderLayout.CENTER);
    inspectorBody.add(stateSection, BorderLayout.SOUTH);
    inspector = new Inspector(S.get("propertiesTab"));
    inspector.setContent(inspectorBody);

    bottomPanel = new BottomPanel();
    bottomPanel.addPanel(
        VHDL_BOTTOM_PANEL,
        S.get("vhdlConsoleTab"),
        AppIcons.get(AppIcons.Id.TERMINAL, 14),
        vhdlSimulatorConsole);

    statusBar = new StatusBar();
    zoomPill.setZoomTextListener(statusBar::setZoom);
    simulationToolbar = new Toolbar(new SimulationToolbarModel(project, menuListener));
    final var mainToolbar = new MainToolbar(toolbar, simulationToolbar);

    LayoutPrefs.migrate(AppPreferences.WINDOW_WIDTH.get(), AppPreferences.WINDOW_HEIGHT.get());
    shell =
        new ShellLayout(
            mainToolbar, activityBar, sidePanel, editorArea, inspector, bottomPanel, statusBar);
    editorTabModel.setEmptyListener(() -> {
      editorClosed = true;
      welcomeShowing = false;
      mainPanel.setView(EDIT_EMPTY);
      editorArea.setTabsVisible(false);
      zoomPill.setVisible(false);
      toolbar.setVisible(false);
      menuListener.setEditHandler(null);
      setAttrTableModel(null);
      circuitList.clearEditorSelection();
      emptyEditor.requestFocusInWindow();
    });
    circuitList.setOnOpen(() -> {
      if (!editorClosed && !welcomeShowing) return;
      dismissWelcome();
      if (project.getCurrentHdl() != null) {
        setHdlEditorView(project.getCurrentHdl());
        syncEditorTabs();
      } else {
        setEditorView(EDIT_LAYOUT);
        syncEditorTabs();
      }
    });
    activityBar.addAction("properties", AppIcons.Id.INSPECTOR, S.get("propertiesTab"),
        () -> shell.setInspectorVisible(!shell.isInspectorVisible()));
    activityBar.addAction("timing", AppIcons.Id.WAVEFORM, S.get("logPanelTab"), this::showLogPanel);
    activityBar.addAction("testVectors", AppIcons.Id.TEST, S.get("testPanelTab"), this::showTestPanel);
    activityBar.addAction("vhdlConsole", AppIcons.Id.TERMINAL, S.get("vhdlConsoleTab"),
        () -> shell.showBottomPanel(VHDL_BOTTOM_PANEL));
    // Both panels could be opened but never shut; their close buttons drive the shell.
    inspector.setOnClose(() -> shell.setInspectorVisible(false));
    bottomPanel.setOnClose(() -> shell.setBottomVisible(false));
    getContentPane().add(shell, BorderLayout.CENTER);

    localeChanged();

    final var screen = getGraphicsConfiguration().getBounds();
    final var screenInsets = getToolkit().getScreenInsets(getGraphicsConfiguration());
    final var availableWidth = screen.width - screenInsets.left - screenInsets.right;
    final var availableHeight = screen.height - screenInsets.top - screenInsets.bottom;
    // The old fresh default used half the width but the entire screen height, squeezing the
    // editor between two full-size sidebars. Preserve saved sizes, bounded by the current screen.
    final var prefs = AppPreferences.getPrefs();
    final var initialWidth = prefs.get("windowWidth", null) == null
        ? availableWidth * 9 / 10 : AppPreferences.WINDOW_WIDTH.get();
    final var initialHeight = prefs.get("windowHeight", null) == null
        ? availableHeight * 9 / 10 : AppPreferences.WINDOW_HEIGHT.get();
    this.setSize(Math.max(1, Math.min(initialWidth, availableWidth)),
        Math.max(1, Math.min(initialHeight, availableHeight)));
    final var prefPoint = getInitialLocation();
    if (prefPoint != null) {
      this.setLocation(prefPoint);
    }
    this.setExtendedState(AppPreferences.WINDOW_STATE.get());

    menuListener.register(mainPanel);
    keyboardToolSelection = KeyboardToolSelection.register(toolbar);

    project.setFrame(this);
    if (project.getTool() == null) {
      project.setTool(project.getOptions().getToolbarData().getFirstTool());
    }
    mainPanel.addChangeListener(myProjectListener);
    AppPreferences.TOOLBAR_PLACEMENT.addPropertyChangeListener(myProjectListener);
    placeToolbar();
    installFileDropTargets();

    LocaleManager.addLocaleListener(this);
    toolbox.updateStructure();

    syncEditorTabs();
    updateCircuitTrail();
    updateZoomStatus();
    restoreLayoutView(project.getCurrentCircuit());
  }

  private final class FileDropTargetListener extends DropTargetAdapter {
    @Override
    public void dragEnter(DropTargetDragEvent event) {
      if (supportsFileDrop(event.getCurrentDataFlavors())) {
        event.acceptDrag(DnDConstants.ACTION_COPY);
      } else {
        event.rejectDrag();
      }
    }

    @Override
    public void dragOver(DropTargetDragEvent event) {
      if (supportsFileDrop(event.getCurrentDataFlavors())) {
        event.acceptDrag(DnDConstants.ACTION_COPY);
      } else {
        event.rejectDrag();
      }
    }

    @Override
    public void drop(DropTargetDropEvent event) {
      if (!supportsFileDrop(event.getCurrentDataFlavors())) {
        event.rejectDrop();
        return;
      }

      var success = false;
      event.acceptDrop(DnDConstants.ACTION_COPY);
      try {
        final var transferable = event.getTransferable();
        final var droppedData = transferable.getTransferData(DataFlavor.javaFileListFlavor);
        if (droppedData instanceof List<?> files) {
          success = openDroppedFiles(files);
        }
      } catch (Exception ex) {
        success = false;
      }
      event.dropComplete(success);
    }
  }

  /**
   * Applies the shared appearance to one of the docked tab strips.
   *
   * <p>The font comes from {@link UiFonts} so the tabs follow the look and feel and the interface
   * scale, which the previous fixed nine point font did not. The card styling is a hint that FlatLaf
   * themes honour and other look and feels ignore.
   */
  private static void styleDockTabs(JTabbedPane tabs) {
    tabs.setFont(UiFonts.body());
    tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_TYPE,
        FlatClientProperties.TABBED_PANE_TAB_TYPE_CARD);
  }

  private void installFileDropTargets() {
    final var listener = new FileDropTargetListener();
    final Set<java.awt.Component> seen = Collections.newSetFromMap(new IdentityHashMap<>());

    installFileDropTarget(this, listener, seen);
    installFileDropTarget(getJMenuBar(), listener, seen);
    installFileDropTarget(getRootPane(), listener, seen);
    installFileDropTarget(getLayeredPane(), listener, seen);
    installFileDropTarget(getContentPane(), listener, seen);
  }

  private void installFileDropTarget(
      java.awt.Component component, FileDropTargetListener listener, Set<java.awt.Component> seen) {
    if (component == null || !seen.add(component)) {
      return;
    }

    new DropTarget(component, DnDConstants.ACTION_COPY, listener, true);
    if (component instanceof Container container) {
      for (final var child : container.getComponents()) {
        installFileDropTarget(child, listener, seen);
      }
    }
  }

  private boolean openDroppedFiles(List<?> droppedFiles) {
    var openedAny = false;
    for (final var item : droppedFiles) {
      if (!(item instanceof File file)) {
        continue;
      }

      if (!isProjectFile(file) && !confirmOpenNonProjectFile(file)) {
        continue;
      }

      openedAny |= openDroppedFile(file);
    }
    return openedAny;
  }

  private boolean openDroppedFile(File file) {
    final var shouldReuseCurrent = shouldOpenDropInCurrentWindow();
    final var previousStartupScreen = project.isStartupScreen();
    if (shouldReuseCurrent && !previousStartupScreen) {
      project.setStartupScreen(true);
    }

    final var openedProject = ProjectActions.doOpen(this, project, file);
    if (shouldReuseCurrent && openedProject != project) {
      project.setStartupScreen(previousStartupScreen);
    }
    return openedProject != null;
  }

  private boolean shouldOpenDropInCurrentWindow() {
    final var loader = project.getLogisimFile().getLoader();
    final var currentCircuit = project.getCurrentCircuit();
    return loader != null
        && loader.getMainFile() == null
        && !project.isFileDirty()
        && currentCircuit != null
        && currentCircuit.getBounds().getWidth() == 0
        && currentCircuit.getBounds().getHeight() == 0;
  }

  private boolean confirmOpenNonProjectFile(File file) {
    final var message =
        S.get("dragOpenNonProjectMessage", file.getAbsolutePath(), Loader.LOGISIM_EXTENSION);
    final var result =
        OptionPane.showConfirmDialog(
            this,
            message,
            S.get("dragOpenNonProjectTitle"),
            OptionPane.YES_NO_OPTION,
            OptionPane.QUESTION_MESSAGE);
    return result == OptionPane.YES_OPTION;
  }

  private boolean isProjectFile(File file) {
    final var name = file.getName().toLowerCase(Locale.ROOT);
    return name.endsWith(Loader.LOGISIM_EXTENSION);
  }

  private boolean supportsFileDrop(DataFlavor[] flavors) {
    for (final var flavor : flavors) {
      if (DataFlavor.javaFileListFlavor.equals(flavor)) {
        return true;
      }
    }
    return false;
  }

  public RegTabContent getRegTabContent() {
    return regTabContent;
  }

  /**
   * Computes allowed zoom steps.
   *
   * @return
   */
  private ArrayList<Double> buildZoomSteps() {
    // Pairs must be in acending order (sorted by maxZoom value).
    final var config = new ZoomStepPair[] {new ZoomStepPair(50, 5), new ZoomStepPair(200, 10), new ZoomStepPair(1000, 20)};

    // Result zoomsteps.
    final var steps = new ArrayList<Double>();
    var zoom = 0D;
    for (final var pair : config) {
      while (zoom < pair.maxZoom()) {
        zoom += pair.step();
        steps.add(zoom);
      }
    }
    return steps;
  }

  private record ZoomStepPair(int maxZoom, int step) {}


  private static Point getInitialLocation() {
    final var s = AppPreferences.WINDOW_LOCATION.get();
    if (s == null) return null;
    final var comma = s.indexOf(',');
    if (comma < 0) return null;
    try {
      var x = Integer.parseInt(s.substring(0, comma));
      var y = Integer.parseInt(s.substring(comma + 1));
      while (isProjectFrameAt(x, y)) {
        x += 20;
        y += 20;
      }
      final var desired = new Rectangle(x, y, 50, 50);

      var gcBestSize = 0;
      Point gcBestPoint = null;
      final var ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      for (final var gd : ge.getScreenDevices()) {
        for (final var gc : gd.getConfigurations()) {
          final var gcBounds = gc.getBounds();
          if (gcBounds.intersects(desired)) {
            final var inter = gcBounds.intersection(desired);
            final var size = inter.width * inter.height;
            if (size > gcBestSize) {
              gcBestSize = size;
              int x2 = Math.max(gcBounds.x, Math.min(inter.x, inter.x + inter.width - 50));
              int y2 = Math.max(gcBounds.y, Math.min(inter.y, inter.y + inter.height - 50));
              gcBestPoint = new Point(x2, y2);
            }
          }
        }
      }
      if (gcBestPoint != null) {
        if (isProjectFrameAt(gcBestPoint.x, gcBestPoint.y)) {
          gcBestPoint = null;
        }
      }
      return gcBestPoint;
    } catch (Throwable t) {
      return null;
    }
  }

  private static boolean isProjectFrameAt(int x, int y) {
    for (final var current : Projects.getOpenProjects()) {
      final var frame = current.getFrame();
      if (frame != null) {
        final var loc = frame.getLocationOnScreen();
        final var d = Math.abs(loc.x - x) + Math.abs(loc.y - y);
        if (d <= 3) {
          return true;
        }
      }
    }
    return false;
  }

  public void resetLayout() {
    shell.resetLayout();
  }

  public Toolbar getToolbar() {
    return toolbar;
  }

  /**
   * Generates String to be used as generic Frame title, taking
   * names of circuits or app version or type.
   */
  private void buildTitleString() {
    final var circuit = project.getCurrentCircuit();
    final var name = project.getLogisimFile().getName();
    final var title = new StringBuilder();

    title
        .append(project.isFileDirty() ? (Main.DIRTY_MARKER + " ") : "")
        .append(
            (circuit != null)
                ? S.get("titleCircFileKnown", circuit.getName(), name)
                : S.get("titleFileKnown", name))
        .append(" · ")
        .append(BuildInfo.displayName);

    // The icon alone may sometimes be missed so we add additional "[UNSAVED]" to the title too.
    if (project.isFileDirty()) {
      title.append(String.format(" [%s]", S.get("titleUnsavedProjectState").toUpperCase()));
    }

    this.setTitle(title.toString().trim());
    myProjectListener.enableSave();
  }

  public boolean confirmClose() {
    return confirmClose(S.get("confirmCloseTitle"));
  }

  // returns true if user is OK with proceeding
  public boolean confirmClose(String title) {
    return ProjectCloseConfirmation.confirm(
        project.isFileDirty(),
        () -> {
          toFront();
          final var message = S.get("confirmDiscardMessage", project.getLogisimFile().getName());
          final String[] options = {
            S.get("saveOption"), S.get("discardOption"), S.get("cancelOption")
          };
          return OptionPane.showOptionDialog(
              this, message, title, 0, OptionPane.QUESTION_MESSAGE, null, options, options[0]);
        },
        () -> ProjectActions.doSave(project));
  }

  @Override
  public void dispose() {
    // Confirmation has no cleanup side effects: a later window may cancel a multi-project Quit.
    // Release recovery and canvas resources only when this window really closes.
    if (!closeResourcesReleased && project != null) {
      closeResourcesReleased = true;
      if (layoutCanvas != null) layoutCanvas.closeCanvas();
      if (timer != null) timer.cancel();
      project.getLogisimFile().stopAutosaveThread(true);
    }
    super.dispose();
  }

  /**
   * Shows what a tab stands for, because the user clicked it.
   *
   * <p>The tabs do not own editors: each one names a circuit, and choosing it is the same request
   * the explorer makes. Going through the project rather than straight to the canvas means the
   * explorer, the title bar and the attribute panel all follow, as they already do.
   */
  private void activateEditorTab(EditorTabModel.Tab tab) {
    switch (tab.kind()) {
      case LAYOUT -> {
        if (tab.target() instanceof Circuit circuit) {
          if (project.getCurrentCircuit() != circuit) {
            project.setCurrentCircuit(circuit);
          } else {
            setEditorView(EDIT_LAYOUT);
          }
        }
      }
      case APPEARANCE -> {
        if (tab.target() instanceof Circuit circuit) {
          if (project.getCurrentCircuit() != circuit) project.setCurrentCircuit(circuit);
          setEditorView(EDIT_APPEARANCE);
        }
      }
      case HDL -> {
        if (tab.target() instanceof HdlModel model) project.setCurrentHdlModel(model);
      }
    }
  }

  /** The label for a tab. Asked again on every redraw, so renaming a circuit needs no bookkeeping. */
  private String editorTabTitle(EditorTabModel.Tab tab) {
    if (tab.target() instanceof Circuit circuit) return circuit.getName();
    if (tab.target() instanceof HdlModel model) return model.getName();
    return "";
  }

  /**
   * Shows an editor, unless the welcome screen is up.
   *
   * <p>Opening a project sets a circuit before the user has chosen anything, and that must not
   * replace the welcome screen with an empty grid; the request is remembered and applied when the
   * screen is dismissed.
   */
  private void showEditor(String view) {
    if (editorClosed) {
      editorClosed = false;
      editorArea.setTabsVisible(true);
      placeToolbar();
    }
    if (welcomeShowing) {
      pendingEditorView = view;
      return;
    }
    mainPanel.setView(view);
  }

  /**
   * Shows the welcome screen.
   *
   * <p>Asked for explicitly by the start-up code when no file was named, rather than read from the
   * project's startup-screen flag: that flag is also cleared by loading the default libraries,
   * which happens before anyone has touched anything.
   */
  public void showWelcomeScreen() {
    showWelcome();
  }

  /** Called by the project when its startup-screen flag changes. */
  public void startupScreenChanged(boolean startup) {
    if (startup) {
      showWelcome();
    } else {
      dismissWelcome();
    }
  }

  /**
   * Shows the welcome screen in place of the canvas.
   *
   * <p>The tab strip goes with it: there is nothing open yet, and an empty strip above a welcome
   * page would only say so twice.
   */
  private void showWelcome() {
    welcomeShowing = true;
    mainPanel.setView(EDIT_WELCOME);
    welcomePanel.refresh();
    editorArea.setTabsVisible(false);
    zoomPill.setVisible(false);
  }

  /** Puts the canvas back, once the user has something open. */
  private void dismissWelcome() {
    if (!welcomeShowing) return;
    welcomeShowing = false;
    mainPanel.setView(pendingEditorView == null ? EDIT_LAYOUT : pendingEditorView);
    pendingEditorView = null;
    editorArea.setTabsVisible(true);
    zoomPill.setVisible(true);
  }

  /** Reports the clock rate the simulation is running at. */
  void setStatusTickRate(String text) {
    statusBar.setTickRate(text);
  }

  /** Reports what the simulation is doing, when it is not simply running. */
  void setStatusSimulationState(String text) {
    statusBar.setSimulationState(text);
  }

  /** Shows the zoom level in the status bar, as a percentage. */
  private void updateZoomStatus() {
    final var active = zoom.getZoomModel();
    statusBar.setZoom(active == null ? "" : Math.round(active.getZoomFactor() * 100) + "%");
  }

  /**
   * The heading of the properties panel: what is selected, or the panel's own name when nothing
   * is.
   */
  private String inspectorTitle() {
    final var model = attrTable.getAttrTableModel();
    final var title = model == null ? null : model.getTitle();
    return (title == null || title.isBlank()) ? S.get("propertiesTab") : title;
  }

  /** The name of whatever is being edited, for the status bar. */
  private String circuitName() {
    final var hdl = project.getCurrentHdl();
    if (hdl != null) return hdl.getName();
    final var circuit = project.getCurrentCircuit();
    return circuit == null ? "" : circuit.getName();
  }

  /**
   * Updates the status bar's name and the trail of circuits above it.
   *
   * <p>The trail is the chain of instances the user descended through, which is what tells them
   * they are inside one particular subcircuit rather than looking at that circuit on its own.
   */
  private void updateCircuitTrail() {
    statusBar.setCircuitName(circuitName());

    final var ancestors = new java.util.ArrayList<CircuitState>();
    for (var state = project.getCircuitState(); state != null; state = state.getParentState()) {
      ancestors.add(0, state);
    }
    // The last entry is the circuit on screen, which the name beside the trail already gives.
    if (!ancestors.isEmpty()) ancestors.remove(ancestors.size() - 1);

    final var names = new java.util.ArrayList<String>(ancestors.size());
    for (final var state : ancestors) {
      names.add(state.getCircuit().getName());
    }
    statusBar.setCircuitTrail(
        names, index -> project.setCircuitState(ancestors.get(index)));
  }

  /**
   * Shows the timing diagram and logging options in the drawer.
   *
   * <p>They were a second OS window with a menu bar and a taskbar entry of their own, placed under
   * the main window from the raw screen size, so watching a waveform beside the circuit meant
   * tiling two windows by hand.
   */
  public void showLogPanel() {
    showDockedPanel(
        LOG_BOTTOM_PANEL,
        S.get("logPanelTab"),
        AppIcons.Id.WAVEFORM,
        () -> project.getLogFrame().dockedContent());
  }

  /** The same for the test-vector view. */
  public void showTestPanel() {
    showDockedPanel(
        TEST_BOTTOM_PANEL,
        S.get("testPanelTab"),
        AppIcons.Id.TEST,
        () -> project.getTestFrame().dockedContent());
  }

  private void showDockedPanel(
      String id, String title, AppIcons.Id icon, java.util.function.Supplier<JComponent> content) {
    if (!dockedPanels.contains(id)) {
      bottomPanel.addPanel(id, title, AppIcons.get(icon, 14), content.get());
      dockedPanels.add(id);
    }
    shell.showBottomPanel(id);
  }

  /** Which drawer panels have been built. They are made the first time they are asked for. */
  private final java.util.Set<String> dockedPanels = new java.util.HashSet<>();

  /** Brings one of the side panels to the front, as clicking its button in the activity bar does. */
  public void showSideView(String id) {
    shell.showSideView(id);
  }

  /** Shows a side view, or collapses the panel when that view is already the one showing. */
  private void toggleSideView(String id) {
    shell.toggleSideView(id);
  }

  /** Opens or selects the tab for whatever the project is now showing. */
  private void syncEditorTabs() {
    final var circuit = project.getCurrentCircuit();
    final var hdl = project.getCurrentHdl();
    if (hdl != null) {
      editorTabModel.syncTo(new EditorTabModel.Tab(EditorTabModel.Kind.HDL, hdl));
    } else if (circuit != null) {
      final var kind =
          EDIT_APPEARANCE.equals(mainPanel.getView())
              ? EditorTabModel.Kind.APPEARANCE
              : EditorTabModel.Kind.LAYOUT;
      editorTabModel.syncTo(new EditorTabModel.Tab(kind, circuit));
    }
  }

  public Canvas getCanvas() {
    return layoutCanvas;
  }

  public String getEditorView() {
    if (editorClosed) return EDIT_EMPTY;
    return (getHdlEditorView() != null ? EDIT_HDL : mainPanel.getView());
  }

  public void computeEditMenuEnabled() {
    menuListener.computeEditEnabled();
    menubar.refreshEditUndoRedoItems();
  }

  public void setEditorView(String view) {
    final var curView = mainPanel.getView();
    if (hdlEditor.getHdlModel() == null && curView.equals(view)) return;
    hdlEditor.setHdlModel(null);

    if (view.equals(EDIT_APPEARANCE)) {
      // appearance view
      var app = appearance;
      if (app == null) {
        app = new AppearanceView();
        app.setCircuit(project, project.getCircuitState());
        mainPanel.addView(EDIT_APPEARANCE, app.getCanvasPane());
        appearance = app;
      }
      toolbar.setToolbarModel(app.getToolbarModel());
      app.getAttrTableDrawManager(attrTable).attributesSelected();
      zoom.setZoomModel(app.getZoomModel());
      zoom.setAutoZoomButtonEnabled(false);
      zoomPill.setModel(app.getZoomModel());
      menuListener.setEditHandler(app.getEditHandler());
      showEditor(view);
      syncEditorTabs();
      app.getCanvas().requestFocus();
    } else {
      // layout view
      toolbar.setToolbarModel(layoutToolbarModel);
      zoom.setZoomModel(layoutZoomModel);
      zoom.setAutoZoomButtonEnabled(true);
      zoomPill.setModel(layoutZoomModel);
      menuListener.setEditHandler(layoutEditHandler);
      viewAttributes(project.getTool(), true);
      showEditor(view);
      syncEditorTabs();
      layoutCanvas.requestFocus();
    }
    updateZoomStatus();
  }

  public ZoomControl getZoomControl() {
    return this.zoom;
  }

  public VhdlSimulatorConsole getVhdlSimulatorConsole() {
    return vhdlSimulatorConsole;
  }

  public ZoomModel getZoomModel() {
    return layoutZoomModel;
  }

  private void rememberLayoutView(Object active) {
    if (active instanceof CircuitState state) {
      layoutViewMemory.remember(
          state.getCircuit(), layoutZoomModel, layoutCanvasPane.getViewport().getViewPosition());
    }
  }

  private void restoreLayoutView(Circuit circuit) {
    layoutCanvas.computeSize(true);
    if (layoutViewMemory.restore(
        circuit,
        layoutZoomModel,
        position ->
            restoreViewPosition(
                layoutCanvasPane.getViewport(), layoutCanvas.getPreferredSize(), position))) {
      return;
    }

    SwingUtilities.invokeLater(() -> initializeLayoutView(circuit));
  }

  private void initializeLayoutView(Circuit circuit) {
    if (circuit == null || project.getCurrentCircuit() != circuit) return;

    final var graphics = layoutCanvas.getGraphics();
    final var bounds = graphics == null ? circuit.getBounds() : circuit.getBounds(graphics);
    final var initialZoom =
        ZoomControl.computeInitialZoomFactor(
            bounds,
            layoutCanvasPane.getViewport().getSize(),
            layoutZoomModel.getZoomOptions());
    layoutZoomModel.setZoomFactor(initialZoom);
    SwingUtilities.invokeLater(
        () -> {
          if (project.getCurrentCircuit() == circuit) {
            layoutCanvas.computeSize(true);
            layoutCanvasPane.getViewport().setViewSize(layoutCanvas.getPreferredSize());
            layoutCanvas.center();
          }
        });
  }

  static void restoreViewPosition(JViewport viewport, java.awt.Dimension viewSize, Point position) {
    if (viewport == null || viewSize == null || position == null) return;
    viewport.setViewSize(viewSize);
    viewport.setViewPosition(position);
  }

  @Override
  public void localeChanged() {
    buildTitleString();
    sidePanel.localeChanged();
    circuitList.localeChanged();
    for (final var view : sidePanel.views()) {
      shell.getActivityBar().setTooltip(view.id(), view.title());
    }
    inspector.setTitle(inspectorTitle());
    stateSection.setTitle(S.get("stateTab"));
    bottomPanel.setTitle(VHDL_BOTTOM_PANEL, S.get("vhdlConsoleTab"));
    bottomPanel.setTitle(LOG_BOTTOM_PANEL, S.get("logPanelTab"));
    bottomPanel.setTitle(TEST_BOTTOM_PANEL, S.get("testPanelTab"));
    editorTabs.refresh();
    welcomePanel.refresh();
    emptyEditorHint.setText(S.get("editorEmptyHint"));
    shell.getActivityBar().setTooltip("properties", S.get("propertiesTab"));
    shell.getActivityBar().setTooltip("timing", S.get("logPanelTab"));
    shell.getActivityBar().setTooltip("testVectors", S.get("testPanelTab"));
    shell.getActivityBar().setTooltip("vhdlConsole", S.get("vhdlConsoleTab"));
  }

  /**
   * Shows or hides the toolbar.
   *
   * <p>It no longer moves: the drawing tools sit on the left of the top row and the simulation
   * controls on the right, so those controls are always in the same place. The "Show toolbar"
   * menu item still works.
   */
  private void placeToolbar() {
    final var loc = AppPreferences.TOOLBAR_PLACEMENT.get();
    toolbar.setOrientation(Toolbar.HORIZONTAL);
    toolbar.setVisible(!editorClosed && !AppPreferences.TOOLBAR_HIDDEN.equals(loc));
    getContentPane().validate();
  }

  public void savePreferences() {
    AppPreferences.TICK_FREQUENCY.set(project.getSimulator().getTickFrequency());
    AppPreferences.LAYOUT_SHOW_GRID.setBoolean(layoutZoomModel.getShowGrid());
    AppPreferences.LAYOUT_ZOOM.set(layoutZoomModel.getZoomFactor());
    if (appearance != null) {
      final var appearanceZoom = appearance.getZoomModel();
      AppPreferences.APPEARANCE_SHOW_GRID.setBoolean(appearanceZoom.getShowGrid());
      AppPreferences.APPEARANCE_ZOOM.set(appearanceZoom.getZoomFactor());
    }
    final var state = getExtendedState() & ~JFrame.ICONIFIED;
    AppPreferences.WINDOW_STATE.set(state);
    final var dim = getSize();
    AppPreferences.WINDOW_WIDTH.set(dim.width);
    AppPreferences.WINDOW_HEIGHT.set(dim.height);
    Point loc;
    try {
      loc = getLocationOnScreen();
    } catch (IllegalComponentStateException e) {
      loc = Projects.getLocation(this);
    }
    if (loc != null) AppPreferences.WINDOW_LOCATION.set(loc.x + "," + loc.y);
    shell.savePreferences();
    AppPreferences.WINDOW_EXPLORER_VISIBLE.set(shell.isSideVisible());
    AppPreferences.DIALOG_DIRECTORY.set(JFileChoosers.getCurrentDirectory());
  }

  void setAttrTableModel(AttrTableModel value) {
    attrTable.setAttrTableModel(value);
    inspector.setTitle(inspectorTitle());
    if (value instanceof AttrTableToolModel model) {
      final var tool = model.getTool();
      toolbox.setHaloedTool(tool);
      layoutToolbarModel.setHaloedTool(tool);
    } else {
      toolbox.setHaloedTool(null);
      layoutToolbarModel.setHaloedTool(null);
    }
    if (value instanceof AttrTableComponentModel model) {
      final var circ = model.getCircuit();
      final var comp = model.getComponent();
      layoutCanvas.setHaloedComponent(circ, comp);
    } else {
      layoutCanvas.setHaloedComponent(null, null);
    }
  }

  public void setVhdlSimulatorConsoleStatusVisible() {
    shell.showBottomPanel(VHDL_BOTTOM_PANEL);
  }

  public void setVhdlSimulatorConsoleStatusInvisible() {
    shell.setBottomVisible(false);
  }

  public boolean isExplorerVisible() {
    return shell != null && shell.isSideVisible();
  }

  public void setExplorerVisible(boolean visible) {
    final var oldVisible = isExplorerVisible();
    shell.setSideVisible(visible);
    AppPreferences.WINDOW_EXPLORER_VISIBLE.set(visible);
    if (oldVisible != visible) {
      firePropertyChange(EXPLORER_VIEW, oldVisible, visible);
    }
  }

  public HdlModel getHdlEditorView() {
    return hdlEditor.getHdlModel();
  }

  private void setHdlEditorView(HdlModel hdl) {
    hdlEditor.setHdlModel(hdl);
    zoom.setZoomModel(null);
    zoomPill.setModel(null);
    showEditor(EDIT_HDL);
    toolbar.setToolbarModel(hdlEditor.getToolbarModel());
    updateZoomStatus();
  }

  void viewAttributes(Tool newTool) {
    viewAttributes(null, newTool, false);
  }

  private void viewAttributes(Tool newTool, boolean force) {
    viewAttributes(null, newTool, force);
  }

  private void viewAttributes(Tool oldTool, Tool newTool, boolean force) {
    AttributeSet newAttrs;
    if (newTool == null) {
      newAttrs = null;
      if (!force) {
        return;
      }
    } else {
      newAttrs = newTool.getAttributeSet(layoutCanvas);
    }
    if (newAttrs == null) {
      final var oldModel = attrTable.getAttrTableModel();
      final var same = (oldModel instanceof AttrTableToolModel model) && model.getTool() == oldTool;
      if (!force && !same && !(oldModel instanceof AttrTableCircuitModel)) return;
    }
    if (newAttrs == null) {
      viewCircuitAttributes();
    } else if (newAttrs instanceof SelectionAttributes) {
      attrTableSelectionModel.updateAttributeSet();
      setAttrTableModel(attrTableSelectionModel);
    } else {
      setAttrTableModel(new AttrTableToolModel(project, newTool));
    }
  }

  void viewCircuitAttributes() {
    final var circ = project.getCurrentCircuit();
    if (circ != null) {
      setAttrTableModel(new AttrTableCircuitModel(project, circ));
    } else if (project.getCurrentHdl() instanceof VhdlContent hdl) {
      setAttrTableModel(new AttrTableHdlModel(project, hdl));
    } else {
      setAttrTableModel(null);
    }
  }

  public void viewComponentAttributes(Circuit circ, Component comp) {
    shell.setInspectorVisible(true);
    if (comp == null) {
      setAttrTableModel(null);
    } else {
      setAttrTableModel(new AttrTableComponentModel(project, circ, comp));
    }
  }

  class MyProjectListener implements ProjectListener, LibraryListener, CircuitListener, PropertyChangeListener, ChangeListener {
    public void attributeListChanged(AttributeEvent e) {
      // Do nothing.
    }

    @Override
    public void circuitChanged(CircuitEvent event) {
      if (event.getAction() == CircuitEvent.ACTION_SET_NAME) {
        buildTitleString();
        editorTabs.refresh();
        updateCircuitTrail();
      }
    }

    private void enableSave() {
      final var ok = getProject().isFileDirty();
      getRootPane().putClientProperty("windowModified", ok);
    }

    @Override
    public void libraryChanged(LibraryEvent e) {
      if (e.getAction() == LibraryEvent.SET_NAME) {
        buildTitleString();
      } else if (e.getAction() == LibraryEvent.DIRTY_STATE) {
        buildTitleString();
        enableSave();
        if (!project.isFileDirty()) editorTabModel.clearDirty();
      } else if (e.getAction() == LibraryEvent.REMOVE_TOOL
          && e.getData() instanceof AddTool tool
          && tool.getFactory() instanceof SubcircuitFactory subcircuitFactory) {
        layoutViewMemory.forget(subcircuitFactory.getSubcircuit());
        editorTabModel.removeTarget(subcircuitFactory.getSubcircuit());
      }
    }

    @Override
    public void projectChanged(ProjectEvent event) {
      final var action = event.getAction();

      if (action == ProjectEvent.ACTION_SET_FILE) {
        buildTitleString();
        editorTabModel.clear();
        project.setTool(project.getOptions().getToolbarData().getFirstTool());
        placeToolbar();
        syncEditorTabs();
      } else if (action == ProjectEvent.ACTION_START) {
        // The first edit means the user has started work, so the welcome screen has served its
        // purpose. doAction already clears the project's flag before publishing this event, so
        // its idempotent setter alone cannot dismiss a separately shown welcome surface.
        project.setStartupScreen(false);
        dismissWelcome();
      } else if (action == ProjectEvent.ACTION_COMPLETE) {
        // An edit landed: mark the circuit it changed, so its tab says so.
        editorTabModel.setDirty(project.getCurrentCircuit(), true);
      } else if (action == ProjectEvent.ACTION_SET_STATE) {
        // The trail is how the user knows which instance they are inside, and how they get back.
        updateCircuitTrail();
        if (event.getData() instanceof CircuitState state) {
          if (state.getParentState() != null) {
            // Looking inside a subcircuit: show the simulation tree, which is where that state is.
            shell.showSideView(SIMULATION_SIDE_VIEW);
          }
        }
      } else if (action == ProjectEvent.ACTION_SET_CURRENT) {
        rememberLayoutView(event.getOldData());
        if (event.getData() instanceof Circuit circuit) {
          setEditorView(EDIT_LAYOUT);
          restoreLayoutView(circuit);
          if (appearance != null) {
            appearance.setCircuit(project, project.getCircuitState());
          }
          viewAttributes(project.getTool());
        } else if (event.getData() instanceof HdlModel model) {
          setHdlEditorView(model);
          viewCircuitAttributes();
        } else {
          viewAttributes(project.getTool());
        }
        syncEditorTabs();
        updateCircuitTrail();
        buildTitleString();
      } else if (action == ProjectEvent.ACTION_SET_TOOL) {
        if (attrTable == null) {
          // for startup
          return;
        }
        final var oldTool = (Tool) event.getOldData();
        final var newTool = (Tool) event.getData();
        if (newTool instanceof AddTool && (welcomeShowing || editorClosed)) {
          dismissWelcome();
          setEditorView(EDIT_LAYOUT);
          syncEditorTabs();
        }
        if (!getEditorView().equals(EDIT_APPEARANCE)) {
          viewAttributes(oldTool, newTool, false);
        }
      }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      if (AppPreferences.TOOLBAR_PLACEMENT.isSource(event)) {
        placeToolbar();
      }
    }

    @Override
    public void stateChanged(ChangeEvent event) {
      final var source = event.getSource();
      if (source == mainPanel) {
        firePropertyChange(EDITOR_VIEW, "???", getEditorView());
      }
    }
  }

  class MyWindowListener extends WindowAdapter {

    @Override
    public void windowClosing(WindowEvent e) {
      if (confirmClose(S.get("confirmCloseTitle"))) {
        Frame.this.dispose();
      }
    }

    @Override
    public void windowClosed(WindowEvent e) {
      keyboardToolSelection.close();
    }

    @Override
    public void windowOpened(WindowEvent e) {
      layoutCanvas.computeSize(true);
    }
  }
}
