/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.ttl;

/**
 * TTL 74374: octal rising-edge D flip-flops with three-state outputs, D and Q pins interleaved.
 *
 * <p>Model based on the Texas Instruments SN74LS374 data sheet.
 */
public class Ttl74374 extends AbstractOctalTriStateFlops {
  /**
   * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
   * prevent project files from loading.
   *
   * <p>Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "74374";

  public Ttl74374() {
    super(
        _ID,
        new byte[] {2, 5, 6, 9, 12, 15, 16, 19},
        new String[] {
          "nOE (Output enable, active LOW)", "Q1", "D1", "D2", "Q2", "Q3", "D3", "D4", "Q4", "CLK",
          "Q5", "D5", "D6", "Q6", "Q7", "D7", "D8", "Q8"
        },
        new int[] {2, 3, 6, 7, 11, 12, 15, 16},
        new int[] {1, 4, 5, 8, 10, 13, 14, 17},
        new String[] {
          "OE", "Q1", "D1", "D2", "Q2", "Q3", "D3", "D4", "Q4", null,
          "CLK", "Q5", "D5", "D6", "Q6", "Q7", "D7", "D8", "Q8", null
        });
  }
}
