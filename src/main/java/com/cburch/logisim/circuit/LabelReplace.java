/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import static com.cburch.logisim.circuit.Strings.S;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.wiring.Tunnel;
import com.cburch.logisim.util.SyntaxChecker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds component labels containing a text or pattern and works out which replacements are
 * valid: a new label must be an acceptable HDL name and must not clash with another label of its
 * circuit, counting the other replacements. Tunnels may share labels, so they are only checked
 * for syntax.
 */
public final class LabelReplace {

  /**
   * One label to change.
   *
   * @param problem why the replacement is refused, or null if it can be applied
   */
  public record Match(
      Circuit circuit, Component component, String oldLabel, String newLabel, String problem) {
    public boolean isValid() {
      return problem == null;
    }

    Match refuse(String reason) {
      return new Match(circuit, component, oldLabel, newLabel, reason);
    }
  }

  private LabelReplace() {}

  /**
   * Compiles the search.
   *
   * @throws java.util.regex.PatternSyntaxException if {@code regex} is set and {@code find} is not
   *     a valid regular expression
   */
  public static Pattern pattern(String find, boolean regex, boolean matchCase) {
    final var flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    return Pattern.compile(regex ? find : Pattern.quote(find), flags);
  }

  /**
   * Finds every label in {@code circuits} that {@code pattern} matches and checks its replacement.
   * With a regular expression, {@code replacement} may refer to groups as {@code $1}; otherwise it
   * is literal. Labels the replacement would not change are left out.
   */
  public static List<Match> find(
      Collection<Circuit> circuits, Pattern pattern, String replacement, boolean regex) {
    final var ret = new ArrayList<Match>();
    final var literal = regex ? replacement : Matcher.quoteReplacement(replacement);
    for (final var circuit : circuits) {
      final var found = new ArrayList<Match>();
      for (final var comp : circuit.getNonWires()) {
        final var attrs = comp.getAttributeSet();
        if (!attrs.containsAttribute(StdAttr.LABEL)) continue;
        final var label = attrs.getValue(StdAttr.LABEL);
        if (label == null || label.isEmpty()) continue;
        final var matcher = pattern.matcher(label);
        if (!matcher.find()) continue;
        final String replaced;
        try {
          replaced = matcher.replaceAll(literal);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
          found.add(new Match(circuit, comp, label, label, e.getMessage()));
          continue;
        }
        if (!replaced.equals(label)) found.add(new Match(circuit, comp, label, replaced, null));
      }
      found.sort(
          Comparator.comparing((Match m) -> m.oldLabel())
              .thenComparing(m -> m.component().getLocation()));
      ret.addAll(validate(circuit, found));
    }
    return ret;
  }

  private static List<Match> validate(Circuit circuit, List<Match> matches) {
    final var hdlType = AppPreferences.HdlType.get();
    final var identity = CircuitLabelValidator.labelIdentityForHdlType(hdlType);
    final var renamed = new HashSet<Component>();
    for (final var match : matches) renamed.add(match.component());
    final var unchanged = new HashSet<Component>(circuit.getNonWires());
    unchanged.removeAll(renamed);

    final var ret = new ArrayList<Match>();
    final var accepted = new ArrayList<Match>();
    for (final var match : matches) {
      final var comp = match.component();
      final var label = match.newLabel();
      if (!match.isValid()) {
        ret.add(match);
      } else if (circuit.isEditLocked() || circuit.isComponentEditLocked(comp)) {
        ret.add(match.refuse(S.get("labelReplaceLocked")));
      } else if (label.isEmpty()) {
        ret.add(match);
        accepted.add(match);
      } else if (!SyntaxChecker.isVariableNameAcceptable(label, hdlType, false)) {
        ret.add(match.refuse(S.get("labelReplaceBadName")));
      } else if (comp.getFactory() instanceof Tunnel) {
        ret.add(match);
        accepted.add(match);
      } else if (!Circuit.isCorrectLabelForCurrentHdl(
          circuit.getName(), label, unchanged, comp.getAttributeSet(), comp.getFactory(), false)) {
        ret.add(match.refuse(S.get("labelReplaceTaken")));
      } else if (accepted.stream()
          .anyMatch(
              other ->
                  !(other.component().getFactory() instanceof Tunnel)
                      && CircuitLabelValidator.labelsMatch(other.newLabel(), label, identity))) {
        ret.add(match.refuse(S.get("labelReplaceDuplicate")));
      } else {
        ret.add(match);
        accepted.add(match);
      }
    }
    return ret;
  }
}
