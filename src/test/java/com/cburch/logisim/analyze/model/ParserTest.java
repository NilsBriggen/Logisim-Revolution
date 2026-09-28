/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.analyze.model.Expression.Notation;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parser tests keyed to the precedence table on the "Creating expressions" help page
 * (doc/en/html/guide/analyze/ana-expr.html): NOT binds tightest, then AND (including plain
 * juxtaposition), then XOR, then OR, then XNOR. Every spelling of an operator has the same level.
 */
public class ParserTest {

  /** One row of the help-page table: an operator, how it combines, and all its spellings. */
  private record Level(String name, BinaryOperator<Expression> build, List<String> spellings) {
    @Override
    public String toString() {
      return name;
    }
  }

  private static final Level AND =
      new Level("AND", Expressions::and,
          List.of("", "*", "&", "&&", "·", "⋅", "∙", "∧", "⋀", "AND", "and"));
  private static final Level XOR =
      new Level("XOR", Expressions::xor, List.of("^", "!=", "⊕", "⊻", "≠", "≢", "XOR", "xor"));
  private static final Level OR =
      new Level("OR", Expressions::or, List.of("+", "|", "||", "∨", "⋁", "∥", "OR", "or"));
  private static final Level XNOR =
      new Level("XNOR", Expressions::xnor,
          List.of("==", "=", "⊙", "≡", "⇔", "↔", "EQUALS", "equals"));

  /** Highest precedence first, as on the help page. */
  private static final List<Level> TABLE = List.of(AND, XOR, OR, XNOR);

  private static final List<String> PREFIX_NOT = List.of("~", "!", "-", "¬", "˜", "NOT ", "not ");

  private static final String[] VARS = {"a", "b", "c"};

  private AnalyzerModel model;

  @BeforeEach
  public void setUp() {
    model = new AnalyzerModel();
    for (final var name : VARS) {
      assertDoesNotThrow(() -> model.getInputs().add(Var.parse(name)));
    }
  }

  private Expression parse(String text) {
    return assertDoesNotThrow(() -> Parser.parse(text, model), "failed to parse '" + text + "'");
  }

  private static Expression ref(String name) {
    return Expressions.variable(name);
  }

  private static Stream<Arguments> higherAndLowerLevels() {
    final var args = new ArrayList<Arguments>();
    for (var hi = 0; hi < TABLE.size(); hi++) {
      for (var lo = hi + 1; lo < TABLE.size(); lo++) {
        args.add(Arguments.of(TABLE.get(hi), TABLE.get(lo)));
      }
    }
    return args.stream();
  }

  @ParameterizedTest(name = "{0} binds tighter than {1} in every spelling")
  @MethodSource("higherAndLowerLevels")
  public void higherLevelBindsTighterWhateverTheSpelling(Level hi, Level lo) {
    for (final var hiOp : hi.spellings()) {
      for (final var loOp : lo.spellings()) {
        // a LO b HI c == a LO (b HI c)
        final var loFirst = "a " + loOp + " b " + hiOp + " c";
        final var loExpected = lo.build().apply(ref("a"), hi.build().apply(ref("b"), ref("c")));
        assertEquals(loExpected, parse(loFirst), loFirst);
        // a HI b LO c == (a HI b) LO c
        final var hiFirst = "a " + hiOp + " b " + loOp + " c";
        final var hiExpected = lo.build().apply(hi.build().apply(ref("a"), ref("b")), ref("c"));
        assertEquals(hiExpected, parse(hiFirst), hiFirst);
      }
    }
  }

  @ParameterizedTest(name = "all spellings of {0} share one level")
  @MethodSource("levels")
  public void spellingsOfOneOperatorAreInterchangeableAndLeftAssociative(Level level) {
    final var expected = level.build().apply(level.build().apply(ref("a"), ref("b")), ref("c"));
    for (final var first : level.spellings()) {
      for (final var second : level.spellings()) {
        final var text = "a " + first + " b " + second + " c";
        assertEquals(expected, parse(text), text);
      }
    }
  }

  private static Stream<Level> levels() {
    return TABLE.stream();
  }

  private static Stream<String> prefixNots() {
    return PREFIX_NOT.stream();
  }

  @ParameterizedTest(name = "NOT ''{0}'' binds tighter than every binary operator")
  @MethodSource("prefixNots")
  public void notBindsTighterThanEveryBinaryOperator(String not) {
    for (final var level : TABLE) {
      final var expected =
          level.build().apply(Expressions.not(ref("a")), Expressions.not(ref("b")));
      for (final var op : level.spellings()) {
        final var prefix = not + "a " + op + " " + not + "b";
        assertEquals(expected, parse(prefix), prefix);
        final var postfix = "a' " + op + " b'";
        assertEquals(expected, parse(postfix), postfix);
      }
    }
  }

  @Test
  public void helpPageExamplesAreTheSameExpression() {
    final var expected =
        Expressions.and(Expressions.not(ref("a")), Expressions.or(ref("b"), ref("c")));
    assertEquals(expected, parse("a' (b + c)"));
    assertEquals(expected, parse("!a && (b || c)"));
    assertEquals(expected, parse("NOT a AND (b OR c)"));
  }

  @Test
  public void reportedMixedSpellingsFollowTheTable() {
    // AND above OR regardless of spelling (the reported "a+b*c becomes (a+b)c" bug)
    final var andFirst = Expressions.or(ref("a"), Expressions.and(ref("b"), ref("c")));
    assertEquals(andFirst, parse("a+b*c"));
    assertEquals(andFirst, parse("a | b & c"));
    assertEquals(andFirst, parse("a OR b AND c"));
    assertEquals(andFirst, parse("a + b AND c"));
    assertEquals(andFirst, parse("a OR b c"));
    // "&" used to bind looser than "+"
    assertEquals(Expressions.or(Expressions.and(ref("a"), ref("b")), ref("c")), parse("a & b + c"));
    // XOR sits between AND and OR
    assertEquals(
        Expressions.or(ref("a"), Expressions.xor(ref("b"), ref("c"))), parse("a + b ^ c"));
    assertEquals(
        Expressions.xor(ref("a"), Expressions.and(ref("b"), ref("c"))), parse("a XOR b c"));
  }

  @Test
  public void juxtapositionIsAnAnd() {
    final var abc = Expressions.and(Expressions.and(ref("a"), ref("b")), ref("c"));
    assertEquals(abc, parse("a b c"));
    assertEquals(Expressions.and(ref("a"), Expressions.not(ref("b"))), parse("a ~b"));
    assertEquals(Expressions.and(ref("a"), Expressions.not(ref("b"))), parse("a b'"));
    assertEquals(
        Expressions.and(ref("a"), Expressions.or(ref("b"), ref("c"))), parse("a (b + c)"));
    assertEquals(
        Expressions.and(Expressions.or(ref("a"), ref("b")), ref("c")), parse("(a + b) c"));
    assertEquals(
        Expressions.and(Expressions.or(ref("a"), ref("b")), Expressions.or(ref("b"), ref("c"))),
        parse("(a + b)(b + c)"));
    assertEquals(
        Expressions.or(
            Expressions.and(ref("a"), Expressions.not(ref("b"))),
            Expressions.and(Expressions.not(ref("a")), ref("c"))),
        parse("a b' + a' c"));
    // juxtaposition keeps its place relative to an explicit AND
    assertEquals(
        Expressions.and(Expressions.and(ref("a"), ref("b")), Expressions.not(ref("c"))),
        parse("a & b ~c"));
  }

  @Test
  public void notAppliesToParenthesizedGroups() {
    final var notOr = Expressions.not(Expressions.or(ref("a"), ref("b")));
    assertEquals(notOr, parse("~(a + b)"));
    assertEquals(notOr, parse("(a + b)'"));
    assertEquals(Expressions.and(notOr, ref("c")), parse("~(a + b) c"));
  }

  @Test
  public void outputAssignmentBindsLoosest() {
    assertDoesNotThrow(() -> model.getOutputs().add(Var.parse("x")));
    final var rhs = Expressions.or(ref("a"), Expressions.and(ref("b"), ref("c")));
    for (final var text : List.of("x = a + b c", "x := a + b c", "x : a OR b AND c")) {
      final var parsed =
          assertDoesNotThrow(() -> Parser.parseMaybeAssignment(text, model), text);
      assertTrue(Expression.isAssignment(parsed), text);
      assertEquals(rhs, Expression.getAssignmentExpression(parsed), text);
    }
  }

  @Test
  public void errorsAreStillReported() {
    assertThrows(ParserException.class, () -> Parser.parse("a +", model));
    assertThrows(ParserException.class, () -> Parser.parse("+ a", model));
    assertThrows(ParserException.class, () -> Parser.parse("(a + b", model));
    assertThrows(ParserException.class, () -> Parser.parse("a + b)", model));
    assertThrows(ParserException.class, () -> Parser.parse("a + q", model));
  }

  /**
   * Every notation offered by the analyzer must render expressions that parse back to an
   * equivalent expression, so text copied from the analyzer (or typed by following its display)
   * keeps its meaning.
   */
  @ParameterizedTest
  @EnumSource(value = Notation.class, names = "LATEX", mode = EnumSource.Mode.EXCLUDE)
  public void everyNotationRoundTripsThroughTheParser(Notation notation) {
    final var random = new Random(42);
    for (var i = 0; i < 500; i++) {
      final var expr = randomExpression(random, 4);
      final var text = expr.toString(notation);
      final var parsed = parse(text);
      assertEquivalent(expr, parsed, notation + ": " + text);
    }
  }

  private static Expression randomExpression(Random random, int depth) {
    if (depth == 0 || random.nextInt(4) == 0) {
      return random.nextInt(8) == 0
          ? Expressions.constant(random.nextInt(2))
          : ref(VARS[random.nextInt(VARS.length)]);
    }
    final var a = randomExpression(random, depth - 1);
    return switch (random.nextInt(5)) {
      case 0 -> Expressions.not(a);
      case 1 -> Expressions.and(a, randomExpression(random, depth - 1));
      case 2 -> Expressions.or(a, randomExpression(random, depth - 1));
      case 3 -> Expressions.xor(a, randomExpression(random, depth - 1));
      default -> Expressions.xnor(a, randomExpression(random, depth - 1));
    };
  }

  private static void assertEquivalent(Expression expected, Expression actual, String message) {
    for (var bits = 0; bits < 1 << VARS.length; bits++) {
      final var assignments = new Assignments();
      for (var i = 0; i < VARS.length; i++) {
        assignments.put(VARS[i], ((bits >> i) & 1) != 0);
      }
      assertEquals(expected.evaluate(assignments), actual.evaluate(assignments), message);
    }
  }
}
