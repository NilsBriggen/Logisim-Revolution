/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.base;

import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Font;
import java.util.Arrays;

/**
 * What a VHDL entity does while no external VHDL simulator (QuestaSim/ModelSim) is running.
 *
 * <p>Its outputs are simply unknown. That is not a failure of the circuit: the rest of it must keep
 * simulating, so the entity no longer throws, it only drives UNKNOWN and shows a small badge.
 */
public final class VhdlSimulationFallback {
  private static final Color BADGE_FILL = new Color(0xE0, 0xA0, 0x00);
  private static final int BADGE_SIZE = 12;

  private VhdlSimulationFallback() {}

  /** True when the project's external VHDL simulator is enabled and running. */
  public static boolean isSimulatorActive(Project project) {
    if (project == null) return false;
    final var simulator = project.getVhdlSimulator();
    return simulator.isEnabled() && simulator.isRunning();
  }

  /** Drives every output of the entity UNKNOWN; inputs are left alone. */
  public static void driveOutputsUnknown(InstanceState state) {
    for (final var port : state.getInstance().getPorts()) {
      if (port.getType() != EndData.OUTPUT_ONLY) continue;
      final var width = port.getFixedBitWidth().getWidth();
      final var values = new Value[width];
      Arrays.fill(values, Value.UNKNOWN);
      state.setPort(state.getPortIndex(port), Value.create(values), 1);
    }
  }

  /**
   * Marks the entity with an amber "!" badge in its top-right corner while the external simulator
   * is not running, so unknown outputs have a visible cause. Nothing is drawn in print views.
   */
  public static void paintBadge(InstancePainter painter) {
    paintBadge(painter, isSimulatorActive(painter.getProject()));
  }

  /**
   * Draws the badge unless {@code simulated}: a component the external simulator does not compute
   * (a Verilog module, for instance) passes false and is always marked.
   */
  public static void paintBadge(InstancePainter painter, boolean simulated) {
    if (!painter.getShowState() || painter.isPrintView() || painter.getCircuitState() == null) {
      return;
    }
    if (simulated) return;
    final var bds = painter.getBounds();
    final var gfx = painter.getGraphics();
    final var oldColor = gfx.getColor();
    final var oldFont = gfx.getFont();
    // Top-right corner: the port labels run down both sides from below the centred name, so the
    // bottom corners collide with the last port label; the name leaves the top corners free.
    final var x = bds.getX() + bds.getWidth() - BADGE_SIZE - 3;
    final var y = bds.getY() + 3;
    gfx.setColor(BADGE_FILL);
    gfx.fillOval(x, y, BADGE_SIZE, BADGE_SIZE);
    gfx.setColor(Color.BLACK);
    gfx.drawOval(x, y, BADGE_SIZE, BADGE_SIZE);
    gfx.setFont(oldFont.deriveFont(Font.BOLD, 10f));
    GraphicsUtil.drawCenteredText(gfx, "!", x + BADGE_SIZE / 2, y + BADGE_SIZE / 2);
    gfx.setFont(oldFont);
    gfx.setColor(oldColor);
  }
}
