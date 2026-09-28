/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

// The recent-files store cannot forget an entry within a run, so the test that needs it empty
// goes before the one that adds a file.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WelcomePanelTest {
  @TempDir Path tempDir;

  @Test
  void primaryActionsAreNamedFocusableButtonsAndRefreshDoesNotDuplicateActions() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var creates = new AtomicInteger();
      final var opens = new AtomicInteger();
      final var panel = new WelcomePanel(creates::incrementAndGet, opens::incrementAndGet, file -> {});
      panel.refresh();
      final var buttons = new ArrayList<JButton>();
      collectButtons(panel, buttons);
      assertTrue(buttons.size() >= 2);
      for (final var button : buttons) {
        assertTrue(button.isFocusable());
        assertFalse(button.getAccessibleContext().getAccessibleName().isBlank());
      }
      buttons.get(0).doClick(0);
      buttons.get(1).doClick(0);
      assertEquals(1, creates.get());
      assertEquals(1, opens.get());
    });
  }

  @Test
  void contentIsCentredRatherThanStretchedAgainstTheLeftEdge() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var panel = new WelcomePanel(() -> {}, () -> {}, file -> {});
      final var layout = (GridBagLayout) panel.getLayout();
      final var constraints = layout.getConstraints(panel.getComponent(0));
      assertEquals(GridBagConstraints.NONE, constraints.fill);
      assertEquals(GridBagConstraints.CENTER, constraints.anchor);
      panel.setSize(2000, 1200);
      panel.doLayout();
      final var column = panel.getComponent(0);
      final var left = column.getX();
      final var right = panel.getWidth() - column.getX() - column.getWidth();
      assertTrue(Math.abs(left - right) <= 1, "left " + left + " right " + right);
    });
  }

  @Test
  @Order(1)
  void withoutRecentProjectsTheTutorialAndGuideAreOffered() throws Exception {
    assumeTrue(AppPreferences.getRecentFiles().isEmpty(), "another test left a recent project");
    SwingUtilities.invokeAndWait(() -> {
      final var topics = new ArrayList<String>();
      final var panel = new WelcomePanel(() -> {}, () -> {}, file -> {}, topics::add);
      final var buttons = new ArrayList<JButton>();
      collectButtons(panel, buttons);
      assertEquals(4, buttons.size());
      buttons.get(2).doClick(0);
      buttons.get(3).doClick(0);
      assertEquals(List.of(WelcomePanel.HELP_TUTORIAL, WelcomePanel.HELP_GUIDE), topics);
    });
  }

  @Test
  @Order(2)
  void recentListFollowsProjectsSavedWhileThePanelIsShowing() throws Exception {
    final var prefs = AppPreferences.getPrefs();
    final var saved = new HashMap<String, String>();
    for (var index = 0; index < 10; index++) {
      saved.put("recent" + index, prefs.get("recent" + index, null));
    }
    final var project = tempDir.resolve("welcome-refresh-" + System.nanoTime() + ".circ").toFile();
    final var panel = new WelcomePanel[1];
    try {
      SwingUtilities.invokeAndWait(() -> panel[0] = new WelcomePanel(() -> {}, () -> {}, f -> {}));
      assertFalse(buttonTexts(panel[0]).contains(project.getName()));

      // Saving happens off the event thread as often as on it; the refresh is queued behind it.
      AppPreferences.updateRecentFile(project);
      SwingUtilities.invokeAndWait(
          () -> assertTrue(buttonTexts(panel[0]).contains(project.getName())));
    } finally {
      for (final var entry : saved.entrySet()) {
        if (entry.getValue() == null) {
          prefs.remove(entry.getKey());
        } else {
          prefs.put(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  private static List<String> buttonTexts(Component root) {
    final var buttons = new ArrayList<JButton>();
    collectButtons(root, buttons);
    final var texts = new ArrayList<String>();
    for (final var button : buttons) texts.add(button.getText());
    return texts;
  }

  private static void collectButtons(Component component, List<JButton> target) {
    if (component instanceof JButton button) target.add(button);
    if (component instanceof Container container) {
      for (final var child : container.getComponents()) collectButtons(child, target);
    }
  }
}
