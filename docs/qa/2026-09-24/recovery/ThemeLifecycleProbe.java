/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.SystemTheme;

public class ThemeLifecycleProbe {
  public static void main(String[] args) throws Exception {
    var regular = AppIcons.get(AppIcons.Id.ADD, 16);
    var original = regular.getColorFilter();
    var disabled = AppIcons.disabled(AppIcons.Id.ADD, 16);
    System.out.println("regular_is_disabled_instance=" + (regular == disabled));
    System.out.println("regular_filter_mutated=" + (regular.getColorFilter() != original));
    System.out.println("later_regular_keeps_mutation="
        + (AppIcons.get(AppIcons.Id.ADD, 16).getColorFilter() == regular.getColorFilter()));
    var run = SystemTheme.class.getDeclaredMethod("run", String[].class);
    run.setAccessible(true);
    var start = System.nanoTime();
    var result = run.invoke(null, (Object) new String[]{"sh", "-c", "sleep 3; printf dark"});
    System.out.println("probe_elapsed_ms=" + (System.nanoTime() - start) / 1_000_000);
    System.out.println("probe_returned=" + result);
  }
}
