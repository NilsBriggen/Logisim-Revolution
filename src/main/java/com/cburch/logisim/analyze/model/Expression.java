/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.model;

import static com.cburch.logisim.analyze.Strings.S;

import com.cburch.logisim.analyze.data.Range;
import com.cburch.logisim.analyze.model.Var.Bit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public abstract class Expression {
  public interface Visitor<T> {
    default T visitVariable(String name) {
      return null;
    }

    default T visitConstant(int value) {
      return null;
    }

    default T visitNot(Expression a) {
      return null;
    }

    default T visitBinary(Expression a, Expression b, Op op) {
      a.visit(this);
      b.visit(this);
      return null;
    }

    default T visitAnd(Expression a, Expression b) {
      return visitBinary(a, b, Op.AND);
    }

    default T visitOr(Expression a, Expression b) {
      return visitBinary(a, b, Op.OR);
    }

    default T visitXor(Expression a, Expression b) {
      return visitBinary(a, b, Op.XOR);
    }

    default T visitXnor(Expression a, Expression b) {
      return visitBinary(a, b, Op.XNOR);
    }

    default T visitEq(Expression a, Expression b) {
      return visitBinary(a, b, Op.EQ);
    }
  }

  protected interface IntVisitor {
    int visitVariable(String name);

    int visitConstant(int value);

    int visitNot(Expression a);

    int visitAnd(Expression a, Expression b);

    int visitOr(Expression a, Expression b);

    int visitXor(Expression a, Expression b);

    int visitXnor(Expression a, Expression b);

    int visitEq(Expression a, Expression b);
  }

  public boolean contains(Op o) {
    return o
        == visit(
            new Visitor<Op>() {
              @Override
              public Op visitBinary(Expression a, Expression b, Op op) {
                return (op == o || a.visit(this) == o || b.visit(this) == o) ? o : null;
              }

              @Override
              public Op visitNot(Expression a) {
                return a.visit(this);
              }
            });
  }

  public boolean evaluate(final Assignments assignments) {
    int ret =
        visit(
            new IntVisitor() {
              @Override
              public int visitAnd(Expression a, Expression b) {
                return a.visit(this) & b.visit(this);
              }

              @Override
              public int visitConstant(int value) {
                return value;
              }

              @Override
              public int visitNot(Expression a) {
                return ~a.visit(this);
              }

              @Override
              public int visitOr(Expression a, Expression b) {
                return a.visit(this) | b.visit(this);
              }

              @Override
              public int visitVariable(String name) {
                return assignments.get(name) ? 1 : 0;
              }

              @Override
              public int visitXor(Expression a, Expression b) {
                return a.visit(this) ^ b.visit(this);
              }

              @Override
              public int visitXnor(Expression a, Expression b) {
                return ~(a.visit(this) ^ b.visit(this));
              }

              @Override
              public int visitEq(Expression a, Expression b) {
                return ~(a.visit(this) ^ b.visit(this) & 1);
              }
            });
    return (ret & 1) != 0;
  }

  public enum Notation {
    MATHEMATICAL(0),
    LOGIC(1),
    ALTLOGIC(2),
    PROGBOOLS(3),
    PROGBITS(4),
    LATEX(5);

    public final int id;

    protected final int[] opLvl;
    protected final String[] opSym;

    // The single precedence table shared by the parser and by every notation, highest first.
    // It is the table documented on the "Creating expressions" help page (ana-expr.html); every
    // spelling of an operator (ASCII, Unicode, C-style or spelled-out word) has the same level.
    // NOT: ~ ! ' - ¬ ˜ NOT
    public static final int NOT_PRECEDENCE = 14;
    // AND: juxtaposition, * & && · ⋅ ∙ ∧ ⋀ AND
    public static final int AND_PRECEDENCE = 13;
    public static final int IMPLICIT_AND_PRECEDENCE = AND_PRECEDENCE;
    // XOR: ^ != ⊕ ⊻ ≠ ≢ XOR
    public static final int XOR_PRECEDENCE = 12;
    // OR: + | || ∨ ⋁ ∥ OR
    public static final int OR_PRECEDENCE = 11;
    // XNOR: == = ⊙ ≡ ⇔ ↔ EQUALS
    public static final int XNOR_PRECEDENCE = 10;
    // output assignment ("x := ...", "x = ...") binds loosest
    public static final int EQ_PRECEDENCE = 0;
    // level shared by all binary operators in the logic notations, which therefore always
    // parenthesize mixed operators
    private static final int SHARED_PRECEDENCE = 9;

    Notation(int id) {
      this.id = id;
      // Precedence level and symbol for each of { EQ, XNOR, OR, XOR, AND, NOT }.
      // The levels must agree with the parser's table above, so that every rendered expression
      // parses back to the same expression: a notation may merge levels (adding parentheses)
      // but never reorder them.
      final int[] standard = {
        EQ_PRECEDENCE, XNOR_PRECEDENCE, OR_PRECEDENCE, XOR_PRECEDENCE, AND_PRECEDENCE,
        NOT_PRECEDENCE
      };
      final int[] shared = {
        EQ_PRECEDENCE, SHARED_PRECEDENCE, SHARED_PRECEDENCE, SHARED_PRECEDENCE,
        SHARED_PRECEDENCE, NOT_PRECEDENCE
      };
      switch (id) {
        case 1 -> { // Logic notation: equiv, vee, vee-underbar, cap, tilde
          opLvl = shared;
          opSym = new String[]{" = ", "≡", "∨", "⊻", "∧", "¬"};
        }
        case 2 -> { // Alternative Logic notation: equiv, vee, not-equiv, cap, ell
          opLvl = shared;
          opSym = new String[]{" = ", "≡", "∨", "≢", "∧", "~"};
        }
        case 3 -> { // Programming with booleans notation: ==, ||, !=, &&, !
          opLvl = standard;
          opSym = new String[]{" = ", "==", "||", "!=", "&&", "!"};
        }
        case 4 -> { // Programming with bits notation: ^ ~, |, ^, &, ~
          // "a ^~ b" reads back as "a ^ (~b)": the XNOR is really an XOR whose right operand
          // gets a NOT. Printing it at the AND level parenthesizes its binary operands (other
          // than XNOR chains) and parenthesizes it inside an AND, which keeps that reading right.
          opLvl = new int[]{
            EQ_PRECEDENCE, AND_PRECEDENCE, OR_PRECEDENCE, XOR_PRECEDENCE, AND_PRECEDENCE,
            NOT_PRECEDENCE
          };
          opSym = new String[]{" = ", "^~", "|", "^", "&", "~"};
        }
        case 5 -> { // LaTeX
          opLvl = standard;
          opSym = new String[]{" = ", " \\oplus ", "+", " \\oplus ", " \\cdot ", " \\overline{"};
        }
        default -> { // Mathematical notation: otimes, plus, oplus, times, and overbar
          opLvl = standard;
          opSym = new String[]{" = ", "⊙", "+", "⊕", "⋅", "~"};
        }
      }
    }

    @Override
    public String toString() {
      String key = name().toLowerCase() + "Notation";
      return S.get(key);
    }
  }

  public enum Op {
    EQ(0, 2),
    XNOR(1, 2),
    OR(2, 2),
    XOR(3, 2),
    AND(4, 2),
    NOT(5, 1);
    public final int id;
    public final int arity;

    Op(int id, int arity) {
      this.id = id;
      this.arity = arity;
    }
  }

  //  // Notation choices:
  //  public static final String[][] OPSYM = {
  //    {"=" , "+" , "⊕" , "⋅" , "~", }, // Mathematic
  //    {"=", "∨", "⊕", "∧", "¬", }, // Logic
  //    {"==", "||", "^", "&&", "!",}, // programming
  //    {" = ", " + ", " \\oplus ", " \\cdot ", " \\overline{",}, // LaTeX
  //  };


  public final List<Range> nots = new ArrayList<>();
  public final List<Range> subscripts = new ArrayList<>();
  public final List<Range> marks = new ArrayList<>();
  private Integer[] badness;

  public abstract int getPrecedence(Notation notation);

  public abstract Op getOp();

  public boolean isCircular() {
    final HashSet<Expression> visited = new HashSet<>();
    visited.add(this);
    Object loop = new Object();
    return loop == visit(new Visitor<>() {
      @Override
      public Object visitBinary(Expression a, Expression b, Op op) {
        if (!visited.add(a)) return loop;
        if (a.visit(this) == loop) return loop;
        visited.remove(a);
        if (!visited.add(b)) return loop;
        if (b.visit(this) == loop) return loop;
        visited.remove(b);
        return null;
      }

      @Override
      public Object visitNot(Expression a) {
        if (!visited.add(a)) return loop;
        if (a.visit(this) == loop) return loop;
        visited.remove(a);
        return null;
      }
    });
  }

  public boolean isCnf() {
    final var cnf = new Object();
    return cnf == visit(new Visitor<>() {
      int level = 0;

      @Override
      public Object visitAnd(Expression a, Expression b) {
        if (level > 1) return null;
        final var oldLevel = level;
        level = 1;
        final var ret = a.visit(this) == cnf && b.visit(this) == cnf ? cnf : null;
        level = oldLevel;
        return ret;
      }

      @Override
      public Object visitConstant(int value) {
        return cnf;
      }

      @Override
      public Object visitNot(Expression a) {
        if (level == 2) return null;
        final var oldLevel = level;
        level = 2;
        final var ret = a.visit(this);
        level = oldLevel;
        return ret;
      }

      @Override
      public Object visitOr(Expression a, Expression b) {
        if (level > 0) return null;
        return a.visit(this) == cnf && b.visit(this) == cnf ? cnf : null;
      }

      @Override
      public Object visitVariable(String name) {
        return cnf;
      }

      @Override
      public Object visitXor(Expression a, Expression b) {
        return null;
      }

      @Override
      public Object visitXnor(Expression a, Expression b) {
        return null;
      }

      @Override
      public Object visitEq(Expression a, Expression b) {
        return null;
      }
    });
  }

  Expression removeVariable(final String input) {
    return visit(new Visitor<>() {
      @Override
      public Expression visitAnd(Expression a, Expression b) {
        final var l = a.visit(this);
        final var r = b.visit(this);
        if (l == null) return r;
        if (r == null) return l;
        return Expressions.and(l, r);
      }

      @Override
      public Expression visitConstant(int value) {
        return Expressions.constant(value);
      }

      @Override
      public Expression visitNot(Expression a) {
        final var l = a.visit(this);
        if (l == null) return null;
        return Expressions.not(l);
      }

      @Override
      public Expression visitOr(Expression a, Expression b) {
        final var l = a.visit(this);
        final var r = b.visit(this);
        if (l == null) return r;
        if (r == null) return l;
        return Expressions.or(l, r);
      }

      @Override
      public Expression visitVariable(String name) {
        return name.equals(input) ? null : Expressions.variable(name);
      }

      @Override
      public Expression visitXor(Expression a, Expression b) {
        final var l = a.visit(this);
        final var r = b.visit(this);
        if (l == null) return r;
        if (r == null) return l;
        return Expressions.xor(l, r);
      }

      @Override
      public Expression visitXnor(Expression a, Expression b) {
        final var l = a.visit(this);
        final var r = b.visit(this);
        if (l == null) return r;
        if (r == null) return l;
        return Expressions.xnor(l, r);
      }

      @Override
      public Expression visitEq(Expression a, Expression b) {
        final var l = a.visit(this);
        final var r = b.visit(this);
        if (l == null) return r;
        if (r == null) return l;
        return Expressions.eq(l, r);
      }
    });
  }

  Expression replaceVariable(final String oldName, final String newName) {
    return visit(new Visitor<>() {
      @Override
      public Expression visitAnd(Expression a, Expression b) {
        final var l = a.visit(this);
        final var r = b.visit(this);
        return Expressions.and(l, r);
      }

      @Override
      public Expression visitConstant(int value) {
        return Expressions.constant(value);
      }

      @Override
      public Expression visitNot(Expression a) {
        final var l = a.visit(this);
        return Expressions.not(l);
      }

      @Override
      public Expression visitOr(Expression a, Expression b) {
        final var l = a.visit(this);
        final var r = b.visit(this);
        return Expressions.or(l, r);
      }

      @Override
      public Expression visitVariable(String name) {
        return Expressions.variable(name.equals(oldName) ? newName : name);
      }

      @Override
      public Expression visitXor(Expression a, Expression b) {
        final var l = a.visit(this);
        final var r = b.visit(this);
        return Expressions.xor(l, r);
      }

      @Override
      public Expression visitXnor(Expression a, Expression b) {
        final var l = a.visit(this);
        final var r = b.visit(this);
        return Expressions.xnor(l, r);
      }

      @Override
      public Expression visitEq(Expression a, Expression b) {
        final var l = a.visit(this);
        final var r = b.visit(this);
        return Expressions.eq(l, r);
      }
    });
  }

  @Override
  public String toString() {
    return toString(Notation.MATHEMATICAL);
  }

  public String toString(Notation notation) {
    return toString(notation, false);
  }

  public String toString(Notation notation, boolean reduce) {
    return toString(notation, reduce, null);
  }

  private static final int BADNESS_NOT_BREAK = 15;
  private static final int BADNESS_PARENTESIS_BREAK = 10;
  private static final int BADNESS_CONST_BREAK = 100;
  private static final int BADNESS_VAR_BREAK = 200;
  private static final int BADNESS_AND_BREAK = 5;

  public String toString(Notation notation, boolean reduce, Expression other) {
    final var text = new StringBuilder();
    final var badnessList = new ArrayList<Integer>();
    if (reduce) {
      nots.clear();
      subscripts.clear();
      marks.clear();
    }
    visit(
        new Visitor<Void>() {
          int curBadness = 0;
          boolean andOp = false;
          boolean inXnor = false;

          private void add(String txt) {
            text.append(txt);
            for (int i = 0; i < txt.length(); i++) badnessList.add(curBadness);
          }

          @Override
          public Void visitBinary(Expression a, Expression b, Op op) {
            Range mark = null;
            if (a.equals(other)) {
              mark = new Range();
              mark.startIndex = text.length();
              marks.add(mark);
            }
            final var opLvl = notation.opLvl[op.id];
            final var aLvl = a.getPrecedence(notation);
            final var bLvl = b.getPrecedence(notation);
            if (aLvl < opLvl || (aLvl == opLvl && a.getOp() != op)) {
              curBadness += BADNESS_PARENTESIS_BREAK;
              add("(");
              a.visit(this);
              add(")");
              curBadness -= BADNESS_PARENTESIS_BREAK;
            } else {
              a.visit(this);
            }
            if (mark != null) {
              mark.stopIndex = text.length();
              mark = null;
            }
            add(notation.opSym[op.id]);
            if (b.equals(other)) {
              mark = new Range();
              mark.startIndex = text.length();
              marks.add(mark);
            }
            if (bLvl < opLvl || (bLvl == opLvl && b.getOp() != op)) {
              curBadness += BADNESS_PARENTESIS_BREAK;
              add("(");
              b.visit(this);
              add(")");
              curBadness -= BADNESS_PARENTESIS_BREAK;
            } else {
              b.visit(this);
            }
            if (mark != null) {
              mark.stopIndex = text.length();
            }
            return null;
          }

          @Override
          public Void visitConstant(int value) {
            curBadness += BADNESS_CONST_BREAK;
            add(Integer.toString(value, 16));
            curBadness -= BADNESS_CONST_BREAK;
            return null;
          }

          @Override
          public Void visitNot(Expression a) {
            curBadness += BADNESS_NOT_BREAK;
            final var opLvl = notation.opLvl[Op.NOT.id];
            final var levelOfA = a.getPrecedence(notation);
            if (reduce && notation.equals(Notation.MATHEMATICAL)) {
              final var notData = new Range();
              notData.startIndex = text.length();
              nots.add(notData);
              a.visit(this);
              notData.stopIndex = text.length();
            } else {
              add(notation.opSym[Op.NOT.id]);
              if (notation.equals(Notation.LATEX)) {
                a.visit(this);
                add("} ");
              } else if (levelOfA < opLvl || (levelOfA == opLvl && a.getOp() != Op.NOT)) {
                curBadness += BADNESS_PARENTESIS_BREAK;
                add("(");
                a.visit(this);
                add(")");
                curBadness -= BADNESS_PARENTESIS_BREAK;
              } else {
                a.visit(this);
              }
            }
            curBadness -= BADNESS_NOT_BREAK;
            return null;
          }

          @Override
          public Void visitXnor(Expression a, Expression b) {
            if (inXnor || !notation.equals(Notation.LATEX)) {
              visitBinary(a, b, notation.equals(Notation.LATEX) ? Op.XOR : Op.XNOR);
            } else {
              inXnor = true;
              text.append(" \\overline{");
              visitBinary(a, b, Op.XOR);
              text.append("}");
              inXnor = false;
            }
            return null;
          }

          @Override
          public Void visitVariable(String name) {
            var baseName = name;
            String index = null;
            try {
              final var b = Bit.parse(name);
              baseName = b.name;
              if (b.bitIndex >= 0) index = Integer.toString(b.bitIndex);
            } catch (ParserException except) {
              /* TODO: catch exception */
            }
            curBadness += BADNESS_VAR_BREAK;
            if (reduce && index != null) {
              add(baseName);
              final var subscript = new Range();
              subscript.startIndex = text.length();
              add(index);
              subscript.stopIndex = text.length();
              subscripts.add(subscript);
            } else if (notation.equals(Notation.LATEX)) {
              add(baseName);
              if (index != null) add("_{" + index + "}");
            } else {
              add(name);
            }
            curBadness -= BADNESS_VAR_BREAK;
            return null;
          }

          @Override
          public Void visitAnd(Expression a, Expression b) {
            if (andOp) {
              visitBinary(a, b, Op.AND);
            } else {
              andOp = true;
              curBadness += BADNESS_AND_BREAK;
              visitBinary(a, b, Op.AND);
              curBadness -= BADNESS_AND_BREAK;
              andOp = false;
            }
            return null;
          }
        });
    badness = badnessList.toArray(new Integer[0]);
    return notation.equals(Notation.LATEX) ? "$" + text + "$" : text.toString();
  }

  public Integer[] getBadness() {
    return badness;
  }

  public static boolean isAssignment(Expression expr) {
    return (expr instanceof Expressions.Eq eq)
           ? (eq.exprA instanceof Expressions.Variable)
           : false;
  }

  public static String getAssignmentVariable(Expression expr) {
    return (expr instanceof Expressions.Eq eq)
           ? (eq.exprA instanceof Expressions.Variable) ? eq.exprA.toString() : null
           : null;
  }

  public static Expression getAssignmentExpression(Expression expr) {
    return (expr instanceof Expressions.Eq eq)
           ? (eq.exprA instanceof Expressions.Variable) ? eq.exprB : null
           : null;
  }

  public abstract <T> T visit(Visitor<T> visitor);

  abstract int visit(IntVisitor visitor);

}
