/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.WiringLibrary;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Locks and the project's style survive a save and a reload, are stored only as optional XML that
 * older versions skip, and leave a file without them reading and writing exactly as before.
 */
class EditLockAndStylePersistenceTest {

  @TempDir Path tempDir;

  private final boolean wasHeadless = Main.headless;

  @BeforeEach
  void noDialogs() {
    Main.headless = true;
  }

  @AfterEach
  void restore() {
    Main.headless = wasHeadless;
  }

  @Test
  void locksSurviveSaveAndReload() throws Exception {
    final var file = newFile();
    final var main = file.getMainCircuit();
    final var lockedPin = pin(main, "a", 100);
    pin(main, "b", 200);
    main.setComponentsEditLocked(List.of(lockedPin), true);
    main.setEditLocked(true);

    final var saved = save(file, "locked.circ");
    final var xml = Files.readString(saved.toPath());
    assertTrue(
        Pattern.compile("<circuit[^>]* locked=\"true\"").matcher(xml).find(),
        "the circuit element carries the lock");
    assertEquals(1, count(xml, "<comp[^>]* locked=\"true\""), "only the locked component does");

    final var loaded = load(saved);
    final var circuit = loaded.getMainCircuit();
    assertTrue(circuit.isEditLocked());
    assertEquals(List.of("a"), labels(circuit.getEditLockedComponents()));
  }

  @Test
  void componentLockAloneRoundTrips() throws Exception {
    final var file = newFile();
    final var main = file.getMainCircuit();
    main.setComponentsEditLocked(List.of(pin(main, "a", 100)), true);

    final var loaded = load(save(file, "comp.circ"));

    assertFalse(loaded.getMainCircuit().isEditLocked());
    assertEquals(List.of("a"), labels(loaded.getMainCircuit().getEditLockedComponents()));
  }

  @Test
  void anUnlockedProjectWritesNoLockAtAll() throws Exception {
    final var file = newFile();
    pin(file.getMainCircuit(), "a", 100);
    final var xml = Files.readString(save(file, "plain.circ").toPath());
    assertFalse(xml.contains("locked="), xml);
    assertFalse(xml.contains("name=\"style"), "no style setting is written when none is set");
  }

  @Test
  void lockingChangesNothingElseInTheFile() throws Exception {
    final var file = newFile();
    final var main = file.getMainCircuit();
    final var a = pin(main, "a", 100);
    final var plain = Files.readString(save(file, "before.circ").toPath());
    main.setComponentsEditLocked(List.of(a), true);
    main.setEditLocked(true);
    final var locked = Files.readString(save(file, "after.circ").toPath());

    // Older versions read the file by element and by <a> child; the lock adds neither.
    assertEquals(plain, locked.replace(" locked=\"true\"", ""));
  }

  @Test
  void fileWithoutLocksOrStyleLoadsAsBefore() throws Exception {
    final var file = newFile();
    pin(file.getMainCircuit(), "a", 100);
    final var saved = save(file, "old.circ");
    final var before = Files.readString(saved.toPath());

    final var loaded = load(saved);
    assertFalse(loaded.getMainCircuit().isEditLocked());
    assertTrue(loaded.getMainCircuit().getEditLockedComponents().isEmpty());
    final var style = loaded.getOptions().getAttributeSet();
    assertNull(style.getValue(Options.ATTR_LABEL_FONT));
    assertNull(style.getValue(Options.ATTR_LABEL_COLOR));
    assertNull(style.getValue(Options.ATTR_TEXT_FONT));

    final var again = tempDir.resolve("again.circ").toFile();
    assertTrue(loaded.getLoader().save(loaded, again));
    assertEquals(before, Files.readString(again.toPath()), "nothing new is written back");
  }

  @Test
  void projectStyleSurvivesSaveAndReload() throws Exception {
    final var file = newFile();
    final var attrs = file.getOptions().getAttributeSet();
    final var labelFont = new Font("Serif", Font.ITALIC, 12);
    final var textFont = new Font("Monospaced", Font.PLAIN, 20);
    attrs.setValue(Options.ATTR_LABEL_FONT, labelFont);
    attrs.setValue(Options.ATTR_LABEL_COLOR, new Color(0x12, 0x80, 0x40));
    attrs.setValue(Options.ATTR_TEXT_FONT, textFont);

    final var saved = save(file, "style.circ");
    final var xml = Files.readString(saved.toPath());
    // Inside <options>, as <a> children an older version looks up by name and skips.
    final var options = xml.substring(xml.indexOf("<options>"), xml.indexOf("</options>"));
    assertTrue(options.contains("name=\"styleLabelFont\""), options);
    assertTrue(options.contains("name=\"styleLabelColor\""), options);
    assertTrue(options.contains("name=\"styleTextFont\""), options);

    final var loaded = load(saved).getOptions().getAttributeSet();
    assertEquals(labelFont, loaded.getValue(Options.ATTR_LABEL_FONT));
    assertEquals(new Color(0x12, 0x80, 0x40), loaded.getValue(Options.ATTR_LABEL_COLOR));
    assertEquals(textFont, loaded.getValue(Options.ATTR_TEXT_FONT));
  }

  @Test
  void styleSettingCanBeTakenBackOff() throws Exception {
    final var file = newFile();
    final var attrs = file.getOptions().getAttributeSet();
    attrs.setValue(Options.ATTR_LABEL_FONT, new Font("Serif", Font.BOLD, 10));
    attrs.setValue(Options.ATTR_LABEL_FONT, null);
    final var xml = Files.readString(save(file, "unset.circ").toPath());
    assertFalse(xml.contains("styleLabelFont"), xml);
  }

  private LogisimFile newFile() {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    file.retireAutosaveThread();
    file.addLibrary(loader.getBuiltin().getLibrary(WiringLibrary._ID));
    return file;
  }

  private File save(LogisimFile file, String name) {
    final var circ = tempDir.resolve(name).toFile();
    assertTrue(file.getLoader().save(file, circ));
    return circ;
  }

  private static LogisimFile load(File circ) throws Exception {
    try (final var in = new FileInputStream(circ)) {
      final var file = LogisimFile.loadSub(in, new Loader(null), circ);
      file.retireAutosaveThread();
      return file;
    }
  }

  private static Component pin(Circuit circuit, String label, int y) {
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, label);
    final var comp = Pin.FACTORY.createComponent(Location.create(100, y, false), attrs);
    final var xn = new CircuitMutation(circuit);
    xn.add(comp);
    xn.execute();
    return comp;
  }

  private static List<String> labels(java.util.Collection<Component> comps) {
    return comps.stream().map(c -> c.getAttributeSet().getValue(StdAttr.LABEL)).sorted().toList();
  }

  private static int count(String text, String regex) {
    final var matcher = Pattern.compile(regex).matcher(text);
    var n = 0;
    while (matcher.find()) n++;
    return n;
  }
}
