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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class Implicant implements Comparable<Implicant> {
  private static class TermIterator implements Iterable<Implicant>, Iterator<Implicant> {
    final Implicant source;
    int currentMask = 0;

    TermIterator(Implicant source) {
      this.source = source;
    }

    @Override
    public boolean hasNext() {
      return currentMask >= 0;
    }

    @Override
    public Iterator<Implicant> iterator() {
      return this;
    }

    @Override
    public Implicant next() {
      final var ret = currentMask | source.values;
      final var diffs = currentMask ^ source.unknowns;
      final var diff = diffs ^ ((diffs - 1) & diffs);
      if (diff == 0) {
        currentMask = -1;
      } else {
        currentMask = (currentMask & -diff) | diff;
      }
      return new Implicant(0, ret);
    }

    @Override
    public void remove() {
      // Do nothing.
    }
  }

  private static int getNrOfOnes(int value, int nrOfBits) {
    var nrOfOnes = 0;
    var mask = 1;
    for (var bitIndex = 0; bitIndex < nrOfBits; bitIndex++) {
      if ((value & mask) != 0) nrOfOnes++;
      mask <<= 1;
    }
    return nrOfOnes;
  }

  private static void report(OptimizationContext context, String info) {
    context.report(info);
  }

  private static <T> void addAll(
      Set<T> destination, Collection<T> source, OptimizationContext context) {
    for (final var value : source) {
      context.checkCancelled();
      destination.add(value);
    }
  }

  private static <T> boolean containsAll(
      Set<T> container, Collection<T> contained, OptimizationContext context) {
    for (final var value : contained) {
      context.checkCancelled();
      if (!container.contains(value)) return false;
    }
    return true;
  }

  private static String getGroupRepresentation(int value, int dontCares, int nrOfBits) {
    final var result = new StringBuffer();
    var mask = 1 << (nrOfBits - 1);
    while (mask > 0) {
      if ((dontCares & mask) != 0) {
        result.append("-");
      } else {
        result.append((value & mask) != 0 ? "1" : "0");
      }
      mask >>= 1;
    }
    return result.toString();
  }

  static List<Implicant> computeMinimal(
      int format, AnalyzerModel model, String variable, JTextArea outputArea) {
    final var table = model.getTruthTable();
    final var outputVariableIndex = model.getOutputs().bits.indexOf(variable);
    if (outputVariableIndex < 0) return Collections.emptyList();
    final var nrOfInputs = table.getInputColumnCount();
    if (nrOfInputs > MAXIMAL_NR_OF_INPUTS_FOR_AUTO_MINIMAL_FORM && outputArea == null) {
      return Collections.emptyList();
    }
    final var context = new OptimizationContext(() -> false, text -> {
      if (outputArea == null) return;
      if (SwingUtilities.isEventDispatchThread()) outputArea.append(text);
      else SwingUtilities.invokeLater(() -> outputArea.append(text));
    });
    return computeMinimal(
        format, nrOfInputs, table.getOutputColumn(outputVariableIndex), variable, context);
  }

  static List<Implicant> computeMinimal(
      int format, int nrOfInputs, Entry[] column, String variable, OptimizationContext outputArea) {
    outputArea.checkCancelled();
    // first we do some house keeping
    final var desiredTerm = format == AnalyzerModel.FORMAT_SUM_OF_PRODUCTS ? Entry.ONE : Entry.ZERO;
    final var skippedTerm = desiredTerm == Entry.ONE ? Entry.ZERO : Entry.ONE;
    final var oneHotTable = new HashSet<Integer>();
    var mask = 1;
    for (var bitIndex = 0; bitIndex < nrOfInputs; bitIndex++) {
      oneHotTable.add(mask);
      mask <<= 1;
    }

    // Here we define the first table with all desired terms (minterms or maxterms) and we add also
    // the don´t cares for the primes we have the group (key) and the min/maxterms in this group
    // (HashSet)
    final var primes = new LinkedHashMap<Implicant, HashSet<Implicant>>();
    final var essentialPrimes = new ArrayList<Implicant>();
    // for the currentTable and the nextTable the key is the number of ones in the min/maxterms
    // (groupid) the HashMap keeps track of the group (key) implicant and the number of min/max
    // terms in this group (HashSet)
    final var currentTable = new LinkedHashMap<Integer, HashMap<Implicant, HashSet<Implicant>>>();
    final var newTable = new LinkedHashMap<Integer, HashMap<Implicant, HashSet<Implicant>>>();
    // for terms to cover is the "key" the min/maxterms that need to be covered, and the ArrayList
    // the set of prime covers that cover the key
    final var termsToCover = new LinkedHashMap<Implicant, ArrayList<Implicant>>();
    var allDontCare = true;
    for (var inputCombination = 0; inputCombination < column.length; inputCombination++) {
      outputArea.checkCancelled();
      final var term = column[inputCombination];
      if (term == skippedTerm) {
        allDontCare = false;
        continue;
      }
      final var nrOfOnes = getNrOfOnes(inputCombination, nrOfInputs);
      final var isDontCare = term != desiredTerm;
      final var implicant = new Implicant(inputCombination, isDontCare);
      final var implicantsSet = new LinkedHashSet<Implicant>();
      if (!isDontCare) {
        termsToCover.put(implicant, new ArrayList<>());
        implicantsSet.add(implicant);
        allDontCare = false;
      }
      if (!newTable.containsKey(nrOfOnes)) {
        newTable.put(nrOfOnes, new LinkedHashMap<>());
      }
      newTable.get(nrOfOnes).put(implicant, implicantsSet);
    }

    if (allDontCare) return Collections.emptyList();
    report(outputArea, String.format("\n%s\n", S.fmt("implicantOutputName", variable)));
    // Here the real work starts, we determine all primes
    var couldMerge = false;
    var groupSize = 2;
    do {
      outputArea.checkCancelled();
      report(outputArea, String.format("\n%s", S.fmt("implicantGroupSize", groupSize)));
      var nrOfPrimes = 0L;
      couldMerge = false;
      currentTable.clear();
      currentTable.putAll(newTable);
      newTable.clear();
      var minimalKey = Integer.MAX_VALUE;
      var maximalKey = 0;
      for (var key : currentTable.keySet()) {
        outputArea.checkCancelled();
        if (key < minimalKey) minimalKey = key;
        if (key > maximalKey) maximalKey = key;
      }
      for (var key = minimalKey; key < maximalKey; key++) {
        if (currentTable.containsKey(key) && currentTable.containsKey(key + 1)) {
          // we see if we can merge terms
          for (var termGroup1 : currentTable.get(key).keySet()) {
            outputArea.checkCancelled();
            for (var termGroup2 : currentTable.get(key + 1).keySet()) {
              outputArea.checkCancelled();
              if (termGroup1.unknowns != termGroup2.unknowns) continue;
              final var differenceMask = termGroup1.values ^ termGroup2.values;
              if (oneHotTable.contains(differenceMask)) {
                final var dontCareMask = termGroup1.unknowns | differenceMask;
                final var newValue = (termGroup1.values & differenceMask) == 0 ? termGroup1.values
                                                                               : termGroup2.values;
                final var isDontCareGroup = termGroup1.isDontCare && termGroup2.isDontCare;
                final var newImplicant = new Implicant(dontCareMask, newValue, isDontCareGroup);
                final var newImplicantTerms = new LinkedHashSet<Implicant>();
                couldMerge = true;
                termGroup1.isPrime = termGroup2.isPrime = false;
                addAll(newImplicantTerms, currentTable.get(key).get(termGroup1), outputArea);
                addAll(newImplicantTerms, currentTable.get(key + 1).get(termGroup2), outputArea);
                var found = false;
                if (newTable.containsKey(key)) {
                  // see if the new implicant already is in the set
                  for (final var implicant : newTable.get(key).keySet()) {
                    outputArea.checkCancelled();
                    found |= (implicant.values == newValue) && (implicant.unknowns == dontCareMask);
                  }
                  if (!found) newTable.get(key).put(newImplicant, newImplicantTerms);
                } else {
                  newTable.put(key, new LinkedHashMap<>());
                  newTable.get(key).put(newImplicant, newImplicantTerms);
                }
              }
            }
          }
        }
      }
      // now we add the primes to the set
      for (final var key : currentTable.keySet()) {
        for (final var implicant : currentTable.get(key).keySet()) {
          outputArea.checkCancelled();
          if (implicant.isPrime && !implicant.isDontCare) {
            primes.put(implicant, currentTable.get(key).get(implicant));
            if ((nrOfPrimes % 16L) == 0L) report(outputArea, "\n");
            report(outputArea,
                String.format("%s ",
                    getGroupRepresentation(implicant.values, implicant.unknowns, nrOfInputs)));
            nrOfPrimes++;
          }
        }
      }
      if (nrOfPrimes == 0) report(outputArea, String.format("\n%s", S.get("implicantNoneFound")));
      groupSize <<= 1;
    } while (couldMerge);

    // we build now the table, with for each term which prime it covers
    for (final var prime : primes.keySet()) {
      for (final var term : termsToCover.keySet()) {
        outputArea.checkCancelled();
        if (primes.get(prime).contains(term)) termsToCover.get(term).add(prime);
      }
    }
    // finally we have to find the essential primes
    var couldDoRowReduction = false;
    var couldDoColumnReduction = false;

    report(outputArea, String.format("\n%s", S.get("implicantColumRowReduction")));
    var nrEssentialPrimes = 0L;
    do {
      outputArea.checkCancelled();
      couldDoRowReduction = false;
      couldDoColumnReduction = false;
      final var termsToRemove = new ArrayList<Implicant>();
      // we first try a column reduction
      for (final var term : termsToCover.keySet()) {
        outputArea.checkCancelled();
        final var termInfo = termsToCover.get(term);
        if (termInfo.size() == 1) {
          // we found a prime cover, as this cover only covers this term
          final var prime = termInfo.get(0);
          if (!primes.containsKey(prime)) continue;
          for (final var terms : primes.get(prime)) {
            for (final var currentPrime : primes.keySet()) {
              outputArea.checkCancelled();
              if (currentPrime.equals(prime)) continue;
              couldDoColumnReduction |= primes.get(currentPrime).contains(terms);
              primes.get(currentPrime).remove(terms);
            }
            termsToRemove.add(terms);
          }
          essentialPrimes.add(prime);
          primes.remove(prime);
          if ((nrEssentialPrimes++ % 16L) == 0) report(outputArea, "\n");
          report(outputArea,
              String.format(
                  " %s", getGroupRepresentation(prime.values, prime.unknowns, nrOfInputs)));
        }
      }
      // we do the cleanup
      for (final var term : termsToRemove) {
        outputArea.checkCancelled();
        termsToCover.remove(term);
      }
      // now we perform the row reduction
      // first we look for empty primes
      final var primesToRemove = new LinkedHashSet<Implicant>();
      final var primeHierarchy = new LinkedHashMap<Integer, HashSet<Implicant>>();
      final var nrOfElementGroups = new ArrayList<Integer>();
      for (final var prime : primes.keySet()) {
        outputArea.checkCancelled();
        final var primeElements = primes.get(prime);
        if (primeElements.isEmpty()) {
          primesToRemove.add(prime);
          couldDoRowReduction = true;
        } else {
          final var nrOfElements = primeElements.size();
          if (!primeHierarchy.containsKey(nrOfElements)) {
            primeHierarchy.put(nrOfElements, new LinkedHashSet<>());
          }
          primeHierarchy.get(nrOfElements).add(prime);
          if (!nrOfElementGroups.contains(nrOfElements)) {
            nrOfElementGroups.add(nrOfElements);
          }
        }
      }
      Collections.sort(nrOfElementGroups);
      if (!nrOfElementGroups.isEmpty()) {
        for (var mergeGroupId = nrOfElementGroups.size() - 1; mergeGroupId > 0; mergeGroupId--) {
          for (final var bigPrime : primeHierarchy.get(nrOfElementGroups.get(mergeGroupId))) {
            outputArea.checkCancelled();
            if (primesToRemove.contains(bigPrime)) continue;
            for (var checkGroupId = mergeGroupId - 1; checkGroupId >= 0; checkGroupId--) {
              for (final var smallPrime : primeHierarchy.get(nrOfElementGroups.get(checkGroupId))) {
                outputArea.checkCancelled();
                if (primesToRemove.contains(smallPrime)) continue;
                if (containsAll(primes.get(bigPrime), primes.get(smallPrime), outputArea)) {
                  couldDoRowReduction = true;
                  primesToRemove.add(smallPrime);
                }
              }
            }
          }
        }
      }
      for (final var prime : primesToRemove) {
        outputArea.checkCancelled();
        primes.remove(prime);
        for (final var element : termsToCover.keySet()) {
          outputArea.checkCancelled();
          termsToCover.get(element).remove(prime);
        }
      }
    } while (couldDoRowReduction || couldDoColumnReduction);

    // It is possible that we still have multiple covers left.
    // The minimal cover can be found using Petrick's method
    if (!termsToCover.isEmpty()) {
      final var simplificationExpression = new ArrayList<HashSet<HashSet<Implicant>>>();

      // Populate the HashSet in order to begin Petrick's method
      for (final var term : termsToCover.keySet()) {
        outputArea.checkCancelled();
        final var group = new LinkedHashSet<HashSet<Implicant>>();
        for (final var impl : termsToCover.get(term)) {
          outputArea.checkCancelled();
          final var impli = new LinkedHashSet<Implicant>();
          impli.add(impl);
          group.add(impli);
        }
        simplificationExpression.add(group);
      }

      do {
        outputArea.checkCancelled();
        for (int i = 0; i < simplificationExpression.size(); i++) {
          final var first = simplificationExpression.get(i);
          if (i + 1 >= simplificationExpression.size()) { // If there is only one left, skip it
            break;
          }
          final var second = simplificationExpression.remove(i + 1);

          // If there are two elements left, then combine them
          final var group = new LinkedHashSet<HashSet<Implicant>>();
          for (final var firstConj : first) {
            for (final var secondConj : second) {
              outputArea.checkCancelled();
              final var conj = new LinkedHashSet<Implicant>();
              addAll(conj, firstConj, outputArea);
              addAll(conj, secondConj, outputArea);
              group.add(conj);
            }
          }
          simplificationExpression.set(i, group);
        }

        // After combining elements, apply reductions
        for (final var group : simplificationExpression) {
          final var implGrpToRemove = new LinkedHashSet<HashSet<Implicant>>();
          for (final var conj1 : group) {
            outputArea.checkCancelled();
            if (implGrpToRemove.contains(conj1)) continue;
            for (final var conj2 : group) {
              outputArea.checkCancelled();
              if (conj1 == conj2 || implGrpToRemove.contains(conj2)) continue;
              if (containsAll(conj2, conj1, outputArea)) {
                implGrpToRemove.add(conj2);
                continue;
              }
              if (containsAll(conj1, conj2, outputArea)) {
                implGrpToRemove.add(conj1);
                break;
              }
            }
          }
          if (!implGrpToRemove.isEmpty()) {
            for (final var removed : implGrpToRemove) {
              outputArea.checkCancelled();
              group.remove(removed);
            }
          }
        }
      } while (simplificationExpression.size() > 1);

      // Get the possible covers, only one can element can be left in the ArrayList here
      final var resGroup = simplificationExpression.get(0);

      final var results = new HashMap<Integer, ArrayList<HashSet<Implicant>>>();
      for (final var grp : resGroup) {
        outputArea.checkCancelled();
        if (results.get(grp.size()) == null) {
          results.put(grp.size(), new ArrayList<>());
        }
        results.get(grp.size()).add(grp);
      }

      final var minimumPrimes = results.get(Collections.min(results.keySet()));
      // Select cheapest prime covers now, meaning the ones with most total unknowns
      final var costMap = new HashMap<Integer, ArrayList<HashSet<Implicant>>>();

      for (final var cover : minimumPrimes) {
        outputArea.checkCancelled();
        var unknown = 0;
        for (final var implicant : cover) {
          outputArea.checkCancelled();
          unknown += implicant.getUnknownCount();
        }
        if (!costMap.keySet().contains(unknown)) {
          costMap.put(unknown, new ArrayList<>());
        }
        costMap.get(unknown).add(cover);
      }

      final var cheapestCovers = costMap.get(Collections.max(costMap.keySet()));
      // TODO: Return all possible covers.
      for (final var prime : cheapestCovers.get(0)) {
        outputArea.checkCancelled();
        essentialPrimes.add(prime);
      }
    }

    // TODO: Return multiple minimal covers if present (Petrick's method)
    outputArea.checkCancelled();
    Collections.sort(essentialPrimes);
    return essentialPrimes;
  }

  public static Expression toExpression(
      int format, AnalyzerModel model, List<Implicant> implicants) {
    return toExpression(format, model.getInputs().bits, implicants, OptimizationContext.SILENT);
  }

  static Expression toExpression(
      int format, List<String> inputs, List<Implicant> implicants, OptimizationContext context) {
    if (implicants == null) return null;
    if (format == AnalyzerModel.FORMAT_PRODUCT_OF_SUMS) {
      Expression product = null;
      for (final var implicant : implicants) {
        context.checkCancelled();
        product = Expressions.and(product, implicant.toSum(inputs));
      }
      return product == null ? Expressions.constant(1) : product;
    } else {
      Expression sum = null;
      for (final var implicant : implicants) {
        context.checkCancelled();
        sum = Expressions.or(sum, implicant.toProduct(inputs));
      }
      return sum == null ? Expressions.constant(0) : sum;
    }
  }

  static final Implicant MINIMAL_IMPLICANT = new Implicant(0, -1);
  static final List<Implicant> MINIMAL_LIST = Collections.singletonList(MINIMAL_IMPLICANT);
  public static final int MAXIMAL_NR_OF_INPUTS_FOR_AUTO_MINIMAL_FORM = 6;

  final int unknowns;
  final int values;
  final boolean isDontCare;
  boolean isPrime = true;

  private Implicant(int unknowns, int values) {
    this.unknowns = unknowns;
    this.values = values;
    isDontCare = false;
  }

  private Implicant(int unknowns, int values, boolean dontCareTerm) {
    this.unknowns = unknowns;
    this.values = values;
    isDontCare = dontCareTerm;
  }

  private Implicant(int values, boolean dontCareTerm) {
    this.unknowns = 0;
    this.values = values;
    isDontCare = dontCareTerm;
  }

  @Override
  public int compareTo(Implicant o) {
    if (this.values < o.values) return -1;
    if (this.values > o.values) return 1;
    return Integer.compare(this.unknowns, o.unknowns);
  }

  @Override
  public boolean equals(Object other) {
    return (other instanceof Implicant o) ? this.unknowns == o.unknowns && this.values == o.values
                                          : false;
  }

  public int getRow() {
    return (unknowns != 0) ? -1 : values;
  }

  public Iterable<Implicant> getTerms() {
    return new TermIterator(this);
  }

  public int getUnknownCount() {
    var ret = 0;
    var n = unknowns;
    while (n != 0) {
      n &= (n - 1);
      ret++;
    }
    return ret;
  }

  @Override
  public int hashCode() {
    return (unknowns << 16) | values;
  }

  public Expression toProduct(TruthTable source) {
    return toProduct(inputNames(source));
  }

  private static List<String> inputNames(TruthTable source) {
    final var names = new ArrayList<String>();
    for (var i = 0; i < source.getInputColumnCount(); i++) names.add(source.getInputHeader(i));
    return names;
  }

  private Expression toProduct(List<String> inputs) {
    Expression term = null;
    final var cols = inputs.size();
    for (var i = cols - 1; i >= 0; i--) {
      if ((unknowns & (1 << i)) == 0) {
        var literal = Expressions.variable(inputs.get(cols - 1 - i));
        if ((values & (1 << i)) == 0) literal = Expressions.not(literal);
        term = Expressions.and(term, literal);
      }
    }
    return term == null ? Expressions.constant(1) : term;
  }

  public Expression toSum(TruthTable source) {
    return toSum(inputNames(source));
  }

  private Expression toSum(List<String> inputs) {
    Expression term = null;
    final var cols = inputs.size();
    for (var i = cols - 1; i >= 0; i--) {
      if ((unknowns & (1 << i)) == 0) {
        var literal = Expressions.variable(inputs.get(cols - 1 - i));
        if ((values & (1 << i)) != 0) literal = Expressions.not(literal);
        term = Expressions.or(term, literal);
      }
    }
    return term == null ? Expressions.constant(0) : term;
  }

  static SortedMap<Implicant, String> computePartition(AnalyzerModel model) {
    // The goal is to find a minimal partitioning of each of the regions (of
    // the {0,1}^n hypercube) defined by the truth table output entries
    // (string of zero, one, dont_care, error, etc.). Similar to K-maps, we
    // can fairly easily find all the prime implicants, but unlike K-maps,
    // we can't have overlap, so a different algorithm is called for. Maybe
    // something from set-covering or binary-partition-trees? It's not even
    // obvious what the complexity of this problem is. We'll just go with a
    // simple greedy algorithm and hope for the best: sort the prime
    // implicants, keep accepting non-overlapping ones until we have covered
    // the region.
    final var table = model.getTruthTable();
    final var maxval = (1 << table.getInputColumnCount()) - 1;
    // Determine the set of regions and the first-cut implicants for each
    // region.
    final var regions = new HashMap<String, HashSet<Implicant>>();
    for (var i = 0; i < table.getVisibleRowCount(); i++) {
      final var val = table.getVisibleOutputs(i);
      final var idx = table.getVisibleRowIndex(i);
      final var dc = table.getVisibleRowDcMask(i);
      final var imp = new Implicant(dc, idx);
      final var region = regions.computeIfAbsent(val, k -> new HashSet<>());
      region.add(imp);
    }
    // For each region...
    final var ret = new TreeMap<Implicant, String>();
    for (var it : regions.entrySet()) {
      final var val = it.getKey();
      final var base = it.getValue();

      // Work up to more general implicants.
      final var all = new HashSet<Implicant>();
      var current = base;
      while (!current.isEmpty()) {
        final var next = new HashSet<Implicant>();
        for (final var implicant : current) {
          all.add(implicant);
          for (int j = 1; j <= maxval; j *= 2) {
            if ((implicant.unknowns & j) != 0) continue;
            final var opp = new Implicant(implicant.unknowns, implicant.values ^ j);
            if (!all.contains(opp)) continue;
            final var i = new Implicant(opp.unknowns | j, opp.values);
            next.add(i);
          }
        }
        current = next;
      }

      final var sorted = new ArrayList<>(all);
      sorted.sort(sortByGenerality);
      final var chosen = new ArrayList<Implicant>();
      for (final var implicant : sorted) {
        if (disjoint(implicant, chosen)) {
          chosen.add(implicant);
          ret.put(implicant, val);
        }
      }
    }

    // TODO: in caller: convert implicant to Row and val back to Entry[]
    return ret;
  }

  private static boolean disjoint(Implicant imp, ArrayList<Implicant> chosen) {
    for (final var other : chosen) {
      final var dc = imp.unknowns | other.unknowns;
      if ((imp.values & ~dc) == (other.values & ~dc)) return false;
    }
    return true;
  }

  private static final CompareGenerality sortByGenerality = new CompareGenerality();

  private static class CompareGenerality implements Comparator<Implicant> {
    @Override
    public int compare(Implicant i1, Implicant i2) {
      final var diff = (i2.getUnknownCount() - i1.getUnknownCount());
      if (diff != 0) return diff;
      return (i1.values & ~i1.unknowns) - (i2.values & ~i2.unknowns);
    }
  }
}
