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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.shell.EditorTabModel.Kind;
import com.cburch.logisim.gui.shell.EditorTabModel.Tab;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EditorTabModelTest {

  /** Stands in for a circuit; the model only ever compares these by identity. */
  private record Target(String name) {}

  private final Target alu = new Target("alu");
  private final Target decoder = new Target("decoder");
  private final Target main = new Target("main");

  private EditorTabModel model;
  private List<Tab> activated;
  private int notifications;

  @BeforeEach
  void setUp() {
    model = new EditorTabModel();
    activated = new ArrayList<>();
    notifications = 0;
    model.setNavigator(activated::add);
    model.setListener(ignored -> notifications++);
  }

  private static Tab layout(Object target) {
    return new Tab(Kind.LAYOUT, target);
  }

  @Test
  void openingACircuitAddsATabAndShowsIt() {
    model.open(layout(alu));

    assertEquals(1, model.count());
    assertEquals(0, model.selectedIndex());
    assertEquals(List.of(layout(alu)), activated);
  }

  @Test
  void openingACircuitThatIsAlreadyOpenSelectsItRatherThanAddingAgain() {
    model.open(layout(alu));
    model.open(layout(decoder));
    activated.clear();

    model.open(layout(alu));

    assertEquals(2, model.count());
    assertEquals(0, model.selectedIndex());
    assertEquals(List.of(layout(alu)), activated);
  }

  @Test
  void circuitAndItsAppearanceAreSeparateTabs() {
    model.open(layout(alu));
    model.open(new Tab(Kind.APPEARANCE, alu));

    assertEquals(2, model.count());
    assertEquals(Kind.APPEARANCE, model.selected().kind());
  }

  @Test
  void twoCircuitsWithTheSameNameAreStillTwoTabs() {
    // Targets are compared by identity: two circuits can share a name across libraries.
    model.open(layout(new Target("main")));
    model.open(layout(new Target("main")));

    assertEquals(2, model.count());
  }

  @Test
  void syncingFromTheProjectDoesNotAskForTheTabToBeShownAgain() {
    // The project can change from the explorer or a menu; following that change must not send the
    // change straight back, or the two would push each other back and forth.
    model.syncTo(layout(alu));

    assertEquals(1, model.count());
    assertEquals(0, model.selectedIndex());
    assertTrue(activated.isEmpty());
  }

  @Test
  void selectingATabAsksForItToBeShownExactlyOnce() {
    model.syncTo(layout(alu));
    model.syncTo(layout(decoder));
    activated.clear();

    model.select(0);

    assertEquals(List.of(layout(alu)), activated);
  }

  @Test
  void selectingTheTabAlreadyShowingChangesNothing() {
    model.syncTo(layout(alu));
    activated.clear();

    model.select(0);

    assertTrue(activated.isEmpty());
  }

  @Test
  void closingTheShowingTabMovesToTheOneThatTakesItsPlace() {
    model.syncTo(layout(main));
    model.syncTo(layout(alu));
    model.syncTo(layout(decoder));
    model.select(1);
    activated.clear();

    model.close(1);

    assertEquals(2, model.count());
    assertEquals(layout(decoder), model.selected(), "the tab that moved into the gap");
    assertEquals(List.of(layout(decoder)), activated);
  }

  @Test
  void closingTheLastTabMovesToTheOneBeforeIt() {
    model.syncTo(layout(main));
    model.syncTo(layout(alu));
    model.select(1);

    model.close(1);

    assertEquals(layout(main), model.selected());
  }

  @Test
  void closingATabBeforeTheShowingOneKeepsTheSameTabShowing() {
    model.syncTo(layout(main));
    model.syncTo(layout(alu));
    model.select(1);
    activated.clear();

    model.close(0);

    assertEquals(layout(alu), model.selected());
    assertTrue(activated.isEmpty(), "the shown circuit did not change, so nothing to do");
  }

  @Test
  void closingTheOnlyTabRequestsABlankEditorWithoutNavigatingTheCircuit() {
    model.syncTo(layout(alu));
    activated.clear();
    final var blanks = new java.util.concurrent.atomic.AtomicInteger();
    model.setEmptyListener(blanks::incrementAndGet);

    model.close(0);

    assertEquals(0, model.count());
    assertEquals(-1, model.selectedIndex());
    assertEquals(1, blanks.get());
    assertTrue(activated.isEmpty());
  }

  @Test
  void deletingTheFinalTargetStillRemovesItsTabs() {
    model.syncTo(layout(alu));
    model.syncTo(new Tab(Kind.APPEARANCE, alu));
    activated.clear();

    model.removeTarget(alu);

    assertEquals(0, model.count());
    assertEquals(-1, model.selectedIndex());
    assertTrue(activated.isEmpty(), "deleting must not reopen another view of that target");
  }

  @Test
  void deletingActiveTargetNavigatesOnlyAfterAllItsViewsAreGone() {
    model.syncTo(layout(main));
    model.syncTo(layout(alu));
    model.syncTo(new Tab(Kind.APPEARANCE, alu));
    activated.clear();

    model.removeTarget(alu);

    assertEquals(List.of(layout(main)), activated);
    assertSame(main, model.selected().target());
  }

  @Test
  void deletingACircuitClosesEveryTabShowingIt() {
    model.syncTo(layout(alu));
    model.syncTo(new Tab(Kind.APPEARANCE, alu));
    model.syncTo(layout(decoder));

    model.removeTarget(alu);

    assertEquals(1, model.count());
    assertSame(decoder, model.selected().target());
  }

  @Test
  void unsavedChangesAreMarkedPerCircuitAndClearedOnSave() {
    model.syncTo(layout(alu));
    model.syncTo(layout(decoder));

    model.setDirty(alu, true);

    assertTrue(model.isDirty(layout(alu)));
    assertFalse(model.isDirty(layout(decoder)));

    model.clearDirty();

    assertFalse(model.isDirty(layout(alu)));
  }

  @Test
  void anUnsavedMarkCoversEveryTabOfThatCircuit() {
    model.syncTo(layout(alu));
    model.syncTo(new Tab(Kind.APPEARANCE, alu));

    model.setDirty(alu, true);

    assertTrue(model.isDirty(layout(alu)));
    assertTrue(model.isDirty(new Tab(Kind.APPEARANCE, alu)));
  }

  @Test
  void closingAndReopeningReportsEveryChangeToTheTabStrip() {
    notifications = 0;

    model.open(layout(alu));
    model.close(0);

    assertTrue(notifications >= 2, "the strip must be told to redraw; got " + notifications);
  }

  @Test
  void outOfRangeRequestsAreIgnored() {
    model.open(layout(alu));

    model.select(5);
    model.close(-1);
    model.close(9);

    assertEquals(1, model.count());
    assertEquals(0, model.selectedIndex());
  }

  @Test
  void loadingAnotherProjectDropsEverything() {
    model.syncTo(layout(alu));
    model.setDirty(alu, true);

    model.clear();

    assertEquals(0, model.count());
    assertEquals(-1, model.selectedIndex());
    assertFalse(model.isDirty(layout(alu)));
  }
}
