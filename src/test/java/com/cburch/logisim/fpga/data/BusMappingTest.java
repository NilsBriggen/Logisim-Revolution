/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A whole bus is mapped to a row of board LEDs in one click, and near misses still hit. */
class BusMappingTest {

  private static FpgaIoInformationContainer led(int x, MapListModel.MapInfo selected) {
    final var io = mock(FpgaIoInformationContainer.class);
    when(io.getRectangle()).thenReturn(new BoardRectangle(x, 295, 14, 14));
    when(io.getType()).thenReturn(IoComponentTypes.Led);
    when(io.getNrOfPins()).thenReturn(1);
    when(io.isSelectable()).thenReturn(true);
    when(io.isFree()).thenReturn(true);
    when(io.getSelectedMapInfo()).thenReturn(selected);
    return io;
  }

  private static MapListModel.MapInfo wholeBus(int bits) {
    final var map = mock(MapComponent.class);
    when(map.getNrOfPins()).thenReturn(bits);
    return new MapListModel.MapInfo(-1, map);
  }

  /** Eight LEDs as on a BASYS3: LD0 on the right, 37 pixels apart. */
  private static List<FpgaIoInformationContainer> row(MapListModel.MapInfo selected) {
    final var leds = new ArrayList<FpgaIoInformationContainer>();
    for (var i = 0; i < 8; i++) leds.add(led(649 - 37 * i, selected));
    return leds;
  }

  @Test
  void bitsGoLeftFromTheClickedLed() {
    final var info = wholeBus(4);
    final var leds = row(info);
    final var targets = IoComponentsInformation.busTargets(leds.get(0), leds);
    assertEquals(List.of(leds.get(0), leds.get(1), leds.get(2), leds.get(3)), targets);
  }

  @Test
  void goesRightWhenTheLeftHasNoRoom() {
    final var info = wholeBus(3);
    final var leds = row(info);
    final var targets = IoComponentsInformation.busTargets(leds.get(7), leds);
    assertEquals(List.of(leds.get(7), leds.get(6), leds.get(5)), targets);
  }

  @Test
  void singleBitsAndTooWideBusesUseTheDialog() {
    final var leds = row(new MapListModel.MapInfo(2, mock(MapComponent.class)));
    assertNull(IoComponentsInformation.busTargets(leds.get(0), leds));
    final var wide = wholeBus(9);
    final var row = row(wide);
    assertNull(IoComponentsInformation.busTargets(row.get(0), row));
  }

  @Test
  void nearMissPicksTheClosestComponent() {
    final var leds = row(wholeBus(2));
    assertSame(leds.get(0), IoComponentsInformation.nearest(leds, 649 + 14 + 3, 300, 8));
    assertNull(IoComponentsInformation.nearest(leds, 649 + 14 + 12, 300, 8));
  }
}
