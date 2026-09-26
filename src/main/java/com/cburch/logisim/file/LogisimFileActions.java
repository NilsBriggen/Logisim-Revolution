/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static com.cburch.logisim.file.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.EditLockedException;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.std.base.Text;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.LibraryTools;
import com.cburch.logisim.util.SyntaxChecker;
import com.cburch.logisim.vhdl.base.VhdlContent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;

public final class LogisimFileActions {

  private static class AddCircuit extends Action {
    private final Circuit circuit;

    AddCircuit(Circuit circuit) {
      this.circuit = circuit;
    }

    @Override
    public void doIt(Project proj) {
      proj.getLogisimFile().addCircuit(circuit);
    }

    @Override
    public String getName() {
      return S.get("addCircuitAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().removeCircuit(circuit);
    }
  }

  private static class AddVhdl extends Action {
    private final VhdlContent vhdl;

    AddVhdl(VhdlContent vhdl) {
      this.vhdl = vhdl;
    }

    @Override
    public void doIt(Project proj) {
      proj.getLogisimFile().addVhdlContent(vhdl);
    }

    @Override
    public String getName() {
      return S.get(vhdl.isVerilog() ? "addVerilogAction" : "addVhdlAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().removeVhdl(vhdl);
    }
  }

  /** How to resolve a merged circuit whose name already exists in the project. */
  enum MergeConflictChoice {
    RENAME,
    REPLACE,
    SKIP,
    CANCEL
  }

  /**
   * The answer to one merge name conflict.
   *
   * @param applyToAll reuse the choice for every later conflict of the same merge
   */
  record MergeConflictDecision(MergeConflictChoice choice, boolean applyToAll) {}

  /** Asks how to resolve one merge name conflict. */
  interface MergeConflictResolver {
    MergeConflictDecision resolve(String circuitName, String suggestedName);
  }

  /** The interactive resolver: Rename is the default, closing the dialog cancels the merge. */
  static final MergeConflictResolver ASK_USER =
      (circuitName, suggestedName) -> {
        final var applyToAll = new JCheckBox(S.get("FileMergeApplyToAll"));
        final var message = new Object[] {S.get("FileMergeQuestion", circuitName), applyToAll};
        final var options =
            new String[] {
              S.get("FileMergeRename", suggestedName),
              S.get("FileMergeReplace"),
              S.get("FileMergeCancel"),
              S.get("FileMergeAbort")
            };
        final var response =
            OptionPane.showOptionDialog(
                null,
                message,
                S.get("FileMergeTitle"),
                JOptionPane.DEFAULT_OPTION,
                OptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        final var choice =
            switch (response) {
              case 0 -> MergeConflictChoice.RENAME;
              case 1 -> MergeConflictChoice.REPLACE;
              case 2 -> MergeConflictChoice.SKIP;
              default -> MergeConflictChoice.CANCEL;
            };
        return new MergeConflictDecision(
            choice, applyToAll.isSelected() && choice != MergeConflictChoice.CANCEL);
      };

  /**
   * Merges circuits (and the libraries they need) from another file into the project.
   *
   * <p>The whole merge is a single undoable step: {@link #doIt} runs the actions it is made of
   * itself, records them, and {@link #undo} reverts them in reverse order. A redo replays the
   * recorded actions without asking any question again.
   */
  private static class MergeFile extends Action {
    private record JarRef(File file, String className) {}

    private final ArrayList<Circuit> mergedCircuits = new ArrayList<>();
    private final ArrayList<JarRef> jarLibs = new ArrayList<>();
    private final ArrayList<File> logiLibs = new ArrayList<>();
    private final ArrayList<String> builtinLibs = new ArrayList<>();
    private final ArrayList<Action> performed = new ArrayList<>();
    private boolean executed = false;

    MergeFile(
        LogisimFile mergelib,
        LogisimFile source,
        List<Circuit> selectedCircuits,
        boolean includeDependencies,
        MergeConflictResolver resolver) {
      final var libNames = new HashMap<String, Library>();
      final var toolList = new HashSet<String>();
      final var errors = new HashMap<String, String>();
      var canContinue = true;
      for (final var lib : source.getLibraries()) {
        LibraryTools.buildLibraryList(lib, libNames);
      }
      LibraryTools.buildToolList(source, toolList);
      LibraryTools.removePresentLibraries(mergelib, libNames, false);
      if (LibraryTools.isLibraryConform(
          mergelib, new HashSet<>(), new HashSet<>(), errors)) {
        /* Okay the library is now ready for merge */
        for (final var lib : mergelib.getLibraries()) {
          final var newToolList = new HashSet<String>();
          LibraryTools.buildToolList(lib, newToolList);
          final var ret = LibraryTools.libraryCanBeMerged(toolList, newToolList);
          if (!ret.isEmpty()) {
            final var Location = "";
            final var toolNames = LibraryTools.getToolLocation(source, Location, ret);
            for (final var key : toolNames.keySet()) {
              var solStr = S.get("LibMergeFailure2") + " a) ";
              final var errLoc = toolNames.get(key);
              final var errParts = errLoc.split("->");
              if (errParts.length > 1) {
                solStr = solStr.concat(S.get("LibMergeFailure4", errParts[1]));
              } else {
                solStr = solStr.concat(S.get("LibMergeFailure3", key));
              }
              solStr = solStr.concat(" " + S.get("LibMergeFailure5") + " b) ");
              solStr = solStr.concat(S.get("LibMergeFailure6", lib.getName()));
              errors.put(solStr, S.get("LibMergeFailure1", lib.getName(), key));
            }
            canContinue = false;
          }
          if (!canContinue) continue;
          final var descriptor = mergelib.getLoader().getDescriptor(lib);
          if (descriptor.charAt(0) == LibraryManager.DESC_SEP) {
            // A built-in library ("#Name"): there is no file to locate, the project's own copy of
            // it is added when the merge is carried out.
            builtinLibs.add(lib.getName());
            continue;
          }
          final var splits = descriptor.split(String.valueOf(LibraryManager.DESC_SEP));
          final var isJar = "jar".equals(splits[0]);
          final File theFile;
          try {
            theFile =
                mergelib
                    .getLoader()
                    .getFileFor(splits[1], isJar ? Loader.JAR_FILTER : Loader.LOGISIM_FILTER);
          } catch (LoaderException e) {
            // The user cancelled locating a library the merged circuits need: merge nothing.
            clear();
            return;
          }
          if ("file".equals(splits[0])) {
            logiLibs.add(theFile);
          } else if (isJar) {
            jarLibs.add(new JarRef(theFile, splits.length > 2 ? splits[2] : null));
          }
        }
        if (!canContinue) {
          LibraryTools.showErrors(mergelib.getName(), errors);
          clear();
          return;
        }
        /* Okay merged the missing libraries, now add the circuits */
        MergeConflictChoice choiceForAll = null;
        for (final var circ : getCircuitsToMerge(mergelib, selectedCircuits, includeDependencies)) {
          final var circName = circ.getName().toUpperCase();
          if (toolList.contains(circName)) {
            final var ret = new ArrayList<String>();
            ret.add(circName);
            final var toolNames = LibraryTools.getToolLocation(source, "", ret);
            for (final var key : toolNames.keySet()) {
              final var errLoc = toolNames.get(key);
              final var errParts = errLoc.split("->");
              if (errParts.length > 1) {
                var solStr = S.get("LibMergeFailure2") + " a) ";
                solStr = solStr.concat(S.get("LibMergeFailure4", errParts[1]));
                solStr = solStr.concat(" " + S.get("LibMergeFailure5") + " b) ");
                solStr = solStr.concat(S.get("LibMergeFailure8", circ.getName()));
                errors.put(solStr, S.get("LibMergeFailure7", key, errParts[1]));
                canContinue = false;
              }
            }
            if (canContinue) {
              final var circ1 = LibraryTools.getCircuitFromLibs(source, circName);
              if (circ1 == null) {
                OptionPane.showMessageDialog(
                    null,
                    "Fatal internal error: Cannot find a referenced circuit",
                    "LogosimFileAction:",
                    OptionPane.ERROR_MESSAGE);
                canContinue = false;
              } else {
                final var suggestedName = suggestName(circ.getName(), source);
                var choice = choiceForAll;
                if (choice == null) {
                  final var decision = resolver.resolve(circ.getName(), suggestedName);
                  choice = decision.choice();
                  if (decision.applyToAll()) choiceForAll = choice;
                }
                switch (choice) {
                  case REPLACE -> mergedCircuits.add(circ);
                  case RENAME -> mergedCircuits.add(renamedCopy(circ, suggestedName, mergelib));
                  case SKIP -> {
                    // leave the project's circuit as it is
                  }
                  default -> {
                    clear();
                    return;
                  }
                }
              }
            }
          } else {
            mergedCircuits.add(circ);
          }
        }
        if (!canContinue) {
          LibraryTools.showErrors(mergelib.getName(), errors);
          clear();
          return;
        }
      } else LibraryTools.showErrors(mergelib.getName(), errors);
    }

    /** A free name for a renamed copy, avoiding the project's circuits and tools. */
    private String suggestName(String name, LogisimFile source) {
      final var taken = new HashSet<String>();
      LibraryTools.buildToolList(source, taken);
      for (final var c : source.getCircuits()) taken.add(c.getName().toUpperCase());
      for (final var c : mergedCircuits) taken.add(c.getName().toUpperCase());
      var suggestedName = name;
      var index = 1;
      while (taken.contains(suggestedName.toUpperCase())) {
        suggestedName = name + "_" + index++;
      }
      return suggestedName;
    }

    private static Circuit renamedCopy(Circuit circ, String name, LogisimFile mergelib) {
      final var renamed = new Circuit(name, mergelib, null);
      CircuitAttributes.copyStaticAttributes(renamed.getStaticAttributes(), circ.getStaticAttributes());
      final var mutation = new CircuitMutation(renamed);
      for (final var comp : circ.getNonWires()) {
        mutation.add(comp);
      }
      for (final var wir : circ.getWires()) {
        mutation.add(Wire.create(wir.getEnd0(), wir.getEnd1()));
      }
      mutation.execute();
      if (circ.getAppearance().hasCustomAppearance()) {
        renamed.getAppearance().repairCustomAppearance(circ.getAppearance().getCustomObjectsFromBottom());
      } else if (circ.getAppearance().isLegacyDefaultCustomAppearance()) {
        renamed.getAppearance().useLegacyDefaultCustomAppearance();
      }
      return renamed;
    }

    private void clear() {
      mergedCircuits.clear();
      jarLibs.clear();
      logiLibs.clear();
      builtinLibs.clear();
    }

    boolean isEmpty() {
      return mergedCircuits.isEmpty()
          && jarLibs.isEmpty()
          && logiLibs.isEmpty()
          && builtinLibs.isEmpty();
    }

    private static List<Circuit> getCircuitsToMerge(
        LogisimFile mergelib, List<Circuit> selectedCircuits, boolean includeDependencies) {
      if (selectedCircuits == null || selectedCircuits.isEmpty()) {
        return mergelib.getCircuits();
      }
      if (!includeDependencies) {
        return selectedCircuits;
      }

      final var result = new java.util.LinkedHashSet<Circuit>();
      for (final var circ : selectedCircuits) {
        addCircWithDeps(circ, mergelib, result);
      }
      return new ArrayList<>(result);
    }

    private static void addCircWithDeps(Circuit circ, LogisimFile mergelib, Set<Circuit> visited) {
      LogisimFileActions.addCircWithDeps(circ, mergelib, visited);
    }

    /** Runs one step of the merge and records it so the merge can be undone as a whole. */
    private void perform(Project proj, Action act) {
      if (act == null) return;
      act.doIt(proj);
      performed.add(act);
    }

    @Override
    public void doIt(Project proj) {
      if (executed) {
        // redo: replay the recorded steps
        for (final var act : performed) act.doIt(proj);
        return;
      }
      executed = true;
      final var loader = proj.getLogisimFile().getLoader();
      /* first the built-in libraries, taken from this project's own loader */
      for (final var name : builtinLibs) {
        final var lib = loader.getBuiltin().getLibrary(name);
        if (lib != null && !proj.getLogisimFile().getLibraries().contains(lib)) {
          perform(proj, LogisimFileActions.loadLibrary(lib, proj.getLogisimFile()));
        }
      }
      builtinLibs.clear();
      /* then the jar libraries */
      for (final var jarLib : jarLibs) {
        final var className =
            jarLib.className() != null
                ? jarLib.className()
                : loader.askJarLibraryClass(jarLib.file());
        if (className == null) continue;
        final var lib = loader.loadJarLibrary(jarLib.file(), className);
        if (lib != null) {
          perform(proj, LogisimFileActions.loadLibrary(lib, proj.getLogisimFile()));
        }
      }
      jarLibs.clear();
      /* next we are going to load the logisimfile  libraries */
      for (final var logiLib : logiLibs) {
        final var put = loader.loadLogisimLibrary(logiLib);
        if (put != null) {
          perform(proj, LogisimFileActions.loadLibrary(put, proj.getLogisimFile()));
        }
      }
      logiLibs.clear();
      // this we are going to do in two steps, first add the circuits with inputs,
      // outputs and wires
      for (final var circ : mergedCircuits) {
        Circuit newCircuit = null;
        var replace = false;
        for (final var circs : proj.getLogisimFile().getCircuits()) {
          if (SyntaxChecker.namesEqualForCurrentHdl(circs.getName(), circ.getName())) {
            newCircuit = circs;
            replace = true;
          }
        }
        if (newCircuit == null) newCircuit = new Circuit(circ.getName(), proj.getLogisimFile(), proj);
        CircuitAttributes.copyStaticAttributes(newCircuit.getStaticAttributes(), circ.getStaticAttributes());
        final var result = new CircuitMutation(newCircuit);
        if (replace) {
          result.clear();
        }
        for (final var comp : circ.getNonWires()) {
          if (comp.getFactory() instanceof Pin) {
            result.add(Pin.FACTORY.createComponent(comp.getLocation(), (AttributeSet) comp.getAttributeSet().clone()));
          }
        }
        for (final var wir : circ.getWires()) {
          result.add(Wire.create(wir.getEnd0(), wir.getEnd1()));
        }
        if (!replace) {
          result.execute();
          perform(proj, LogisimFileActions.addCircuit(newCircuit));
        } else {
          perform(proj, result.toAction(S.getter("replaceCircuitAction")));
        }
        if (circ.getAppearance().hasCustomAppearance()) {
          newCircuit.getAppearance().repairCustomAppearance(circ.getAppearance().getCustomObjectsFromBottom());
        } else if (circ.getAppearance().isLegacyDefaultCustomAppearance()) {
          newCircuit.getAppearance().useLegacyDefaultCustomAppearance();
        }
      }
      final var availableTools = new HashMap<String, AddTool>();
      LibraryTools.buildToolList(proj.getLogisimFile(), availableTools);
      // in the second step we are going to add the rest of the contents
      for (final var circ : mergedCircuits) {
        final var newCirc = proj.getLogisimFile().getCircuit(circ.getName());
        if (newCirc != null) {
          final var result = new CircuitMutation(newCirc);
          for (final var comp : circ.getNonWires()) {
            if (!(comp.getFactory() instanceof Pin)) {
              final var current = availableTools.get(comp.getFactory().getName().toUpperCase());
              if (current != null) {
                final var factory = current.getFactory();
                if (factory instanceof SubcircuitFactory subcirc) {
                  final var newAttrs = factory.createAttributeSet();
                  CircuitAttributes.copyInto(comp.getAttributeSet(), newAttrs);
                  result.add(factory.createComponent(comp.getLocation(), newAttrs));
                } else {
                  result.add(factory.createComponent(comp.getLocation(), (AttributeSet) comp.getAttributeSet().clone()));
                }
              } else if ("Text".equals(comp.getFactory().getName())) {
                result.add(Text.FACTORY.createComponent(comp.getLocation(), (AttributeSet) comp.getAttributeSet().clone()));
              } else System.out.println("Not found:" + comp.getFactory().getName());
            }
          }
          perform(proj, result.toAction(S.getter("replaceCircuitAction")));
        }
      }
      mergedCircuits.clear();
    }

    @Override
    public String getName() {
      return S.get("mergeFileAction");
    }

    @Override
    public boolean isModification() {
      return !performed.isEmpty();
    }

    @Override
    public void undo(Project proj) {
      for (var i = performed.size() - 1; i >= 0; i--) {
        performed.get(i).undo(proj);
      }
    }
  }

  private static class LoadLibraries extends Action {
    private final List<Library> mergedLibs = new ArrayList<>();
    private final Set<String> baseLibsToEnable = new HashSet<>();

    LoadLibraries(Library[] libs, LogisimFile source) {
      final var libNames = new HashMap<String, Library>();
      final var toolList = new HashSet<String>();
      for (final var newLib : libs) {
        // first cleanup step: remove unused libraries from loaded library
        LibraryManager.removeUnusedLibraries(newLib);
        // second cleanup step: promote base libraries
        baseLibsToEnable.addAll(LibraryManager.getUsedBaseLibraries(newLib));
      }
      // promote the none visible base libraries to toplevel
      final var builtinLibraries = LibraryManager.getBuildinNames(source.getLoader());
      for (final var lib : source.getLibraries()) {
        final var libName = lib.getName();
        if (baseLibsToEnable.contains(libName) || !builtinLibraries.contains(libName)) {
          baseLibsToEnable.remove(libName);
        }
      }
      // remove the promoted base libraries from the loaded library
      for (final var newLib : libs) {
        LibraryManager.removeBaseLibraries(newLib, baseLibsToEnable);
      }
      for (final var lib : source.getLibraries()) {
        LibraryTools.buildLibraryList(lib, libNames);
      }
      LibraryTools.buildToolList(source, toolList);
      for (final var lib : libs) {
        if (libNames.containsKey(lib.getName().toUpperCase())) {
          OptionPane.showMessageDialog(
              null,
              "\"" + lib.getName() + "\": " + S.get("LibraryAlreadyLoaded"),
              S.get("LibLoadErrors") + " " + lib.getName(),
              OptionPane.WARNING_MESSAGE);
        } else {
          // each library is judged on its own problems, not on those of the one before it
          final var errors = new HashMap<String, String>();
          LibraryTools.removePresentLibraries(lib, libNames, false);
          if (LibraryTools.isLibraryConform(lib, new HashSet<>(), new HashSet<>(), errors)) {
            final var addedToolList = new HashSet<String>();
            LibraryTools.buildToolList(lib, addedToolList);
            final var clashes = new ArrayList<String>();
            for (final var tool : addedToolList) {
              if (toolList.contains(tool)) clashes.add(tool);
            }
            if (clashes.isEmpty()) {
              LibraryTools.buildLibraryList(lib, libNames);
              toolList.addAll(addedToolList);
              mergedLibs.add(lib);
            } else {
              showToolClashes(lib, clashes);
            }
          } else
            LibraryTools.showErrors(lib.getName(), errors);
        }
      }
      if (mergedLibs.isEmpty()) {
        // nothing is loaded, so no base library needs to be promoted either
        baseLibsToEnable.clear();
      }
    }

    /**
     * Tells the user which of the library's circuits/tools clash with names already in the
     * project, in their own spelling, and how to resolve it.
     */
    private static void showToolClashes(Library lib, List<String> upperCaseClashes) {
      final var names =
          new ArrayList<>(LibraryTools.getToolLocation(lib, "", upperCaseClashes).keySet());
      if (names.isEmpty()) names.addAll(upperCaseClashes);
      names.sort(String.CASE_INSENSITIVE_ORDER);
      final var list = new StringBuilder();
      for (final var name : names) list.append("\n    \u2022 ").append(name);
      OptionPane.showMessageDialog(
          null,
          S.get("LibraryToolClash", lib.getName(), list.toString()),
          S.get("LibLoadErrors") + " " + lib.getName(),
          OptionPane.ERROR_MESSAGE);
    }

    boolean isEmpty() {
      return mergedLibs.isEmpty() && baseLibsToEnable.isEmpty();
    }

    @Override
    public void doIt(Project proj) {
      for (final var lib : baseLibsToEnable) {
        final var logisimFile = proj.getLogisimFile();
        logisimFile.addLibrary(logisimFile.getLoader().getBuiltin().getLibrary(lib));
      }
      for (final var lib : mergedLibs) {
        if (lib instanceof LoadedLibrary lib1) {
          if (lib1.getBase() instanceof LogisimFile) {
            repair(proj, lib1.getBase());
          }
        } else if (lib instanceof LogisimFile) {
          repair(proj, lib);
        }
        proj.getLogisimFile().addLibrary(lib);
      }
    }

    private void repair(Project proj, Library lib) {
      final var availableTools = new HashMap<String, AddTool>();
      LibraryTools.buildToolList(proj.getLogisimFile(), availableTools);
      if (lib instanceof LogisimFile thisLib) {
        for (final var circ : thisLib.getCircuits()) {
          for (final var tool : circ.getNonWires()) {
            if (availableTools.containsKey(tool.getFactory().getName().toUpperCase())) {
              final var current = availableTools.get(tool.getFactory().getName().toUpperCase());
              if (current != null) {
                tool.setFactory(current.getFactory());
              } else if ("Text".equals(tool.getFactory().getName())) {
                final var newComp = Text.FACTORY.createComponent(tool.getLocation(), (AttributeSet) tool.getAttributeSet().clone());
                tool.setFactory(newComp.getFactory());
              } else
                System.out.println("Not found:" + tool.getFactory().getName());
            }
          }
        }
      }
      for (final var libs : lib.getLibraries()) {
        repair(proj, libs);
      }
    }

    @Override
    public boolean isModification() {
      return !mergedLibs.isEmpty();
    }

    @Override
    public String getName() {
      return (mergedLibs.size() <= 1) ? S.get("loadLibraryAction") : S.get("loadLibrariesAction");
    }

    @Override
    public void undo(Project proj) {
      for (final var lib : mergedLibs) proj.getLogisimFile().removeLibrary(lib);
      for (final var lib : baseLibsToEnable) proj.getLogisimFile().removeLibrary(lib);
    }
  }

  private static class MoveCircuit extends Action {
    private final AddTool tool;
    private int fromIndex;
    private final int toIndex;

    MoveCircuit(AddTool tool, int toIndex) {
      this.tool = tool;
      this.toIndex = toIndex;
    }

    @Override
    public Action append(Action other) {
      final var ret = new MoveCircuit(tool, ((MoveCircuit) other).toIndex);
      ret.fromIndex = this.fromIndex;
      return ret.fromIndex == ret.toIndex ? null : ret;
    }

    @Override
    public void doIt(Project proj) {
      fromIndex = proj.getLogisimFile().getTools().indexOf(tool);
      proj.getLogisimFile().moveCircuit(tool, toIndex);
    }

    @Override
    public String getName() {
      return S.get("moveCircuitAction");
    }

    @Override
    public boolean shouldAppendTo(Action other) {
      return other instanceof MoveCircuit circ && circ.tool == this.tool;
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().moveCircuit(tool, fromIndex);
    }
  }

  private static class RemoveCircuit extends Action {
    private final Circuit circuit;
    private int index;

    RemoveCircuit(Circuit circuit) {
      this.circuit = circuit;
    }

    @Override
    public void doIt(Project proj) {
      if (circuit.isEditLocked()) throw new EditLockedException(circuit, null);
      index = proj.getLogisimFile().indexOfCircuit(circuit);
      proj.getLogisimFile().removeCircuit(circuit);
    }

    @Override
    public String getName() {
      return S.get("removeCircuitAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().addCircuit(circuit, index);
    }
  }

  private static class RemoveVhdl extends Action {
    private final VhdlContent vhdl;
    private int index;

    RemoveVhdl(VhdlContent vhdl) {
      this.vhdl = vhdl;
    }

    @Override
    public void doIt(Project proj) {
      index = proj.getLogisimFile().indexOfVhdl(vhdl);
      proj.getLogisimFile().removeVhdl(vhdl);
    }

    @Override
    public String getName() {
      return S.get(vhdl.isVerilog() ? "removeVerilogAction" : "removeVhdlAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().addVhdlContent(vhdl, index);
    }
  }

  private static class RevertAttributeValue {
    private final AttributeSet attrs;
    private final Attribute<Object> attr;
    private final Object value;

    RevertAttributeValue(AttributeSet attrs, Attribute<Object> attr, Object value) {
      this.attrs = attrs;
      this.attr = attr;
      this.value = value;
    }
  }

  private static class RevertDefaults extends Action {
    private Options oldOpts;
    private ArrayList<Library> libraries = null;
    private final ArrayList<RevertAttributeValue> attrValues;

    RevertDefaults() {
      libraries = null;
      attrValues = new ArrayList<>();
    }

    private void copyToolAttributes(Library srcLib, Library dstLib) {
      for (final var srcTool : srcLib.getTools()) {
        final var srcAttrs = srcTool.getAttributeSet();
        final var dstTool = dstLib.getTool(srcTool.getName());
        if (srcAttrs != null && dstTool != null) {
          final var dstAttrs = dstTool.getAttributeSet();
          for (Attribute<?> attrBase : srcAttrs.getAttributes()) {
            @SuppressWarnings("unchecked")
            final var attr = (Attribute<Object>) attrBase;
            final var srcValue = srcAttrs.getValue(attr);
            final var dstValue = dstAttrs.getValue(attr);
            if (!dstValue.equals(srcValue)) {
              dstAttrs.setValue(attr, srcValue);
              attrValues.add(new RevertAttributeValue(dstAttrs, attr, dstValue));
            }
          }
        }
      }
    }

    @Override
    public void doIt(Project proj) {
      final var src = ProjectActions.createNewFile(proj);
      final var dst = proj.getLogisimFile();

      copyToolAttributes(src, dst);
      for (final var srcLib : src.getLibraries()) {
        var dstLib = dst.getLibrary(srcLib.getName());
        if (dstLib == null) {
          final var desc = src.getLoader().getDescriptor(srcLib);
          dstLib = dst.getLoader().loadLibrary(desc);
          proj.getLogisimFile().addLibrary(dstLib);
          if (libraries == null) libraries = new ArrayList<>();
          libraries.add(dstLib);
        }
        copyToolAttributes(srcLib, dstLib);
      }

      final var newOpts = proj.getOptions();
      oldOpts = new Options();
      oldOpts.copyFrom(newOpts, dst);
      newOpts.copyFrom(src.getOptions(), dst);
    }

    @Override
    public String getName() {
      return S.get("revertDefaultsAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getOptions().copyFrom(oldOpts, proj.getLogisimFile());

      for (final var attrValue : attrValues) {
        attrValue.attrs.setValue(attrValue.attr, attrValue.value);
      }

      if (libraries != null) {
        for (final var lib : libraries) {
          proj.getLogisimFile().removeLibrary(lib);
        }
      }
    }
  }

  private static class SetMainCircuit extends Action {
    private Circuit oldval;
    private final Circuit newval;

    SetMainCircuit(Circuit circuit) {
      newval = circuit;
    }

    @Override
    public void doIt(Project proj) {
      oldval = proj.getLogisimFile().getMainCircuit();
      proj.getLogisimFile().setMainCircuit(newval);
    }

    @Override
    public String getName() {
      return S.get("setMainCircuitAction");
    }

    @Override
    public void undo(Project proj) {
      proj.getLogisimFile().setMainCircuit(oldval);
    }
  }

  private static class UnloadLibraries extends Action {
    private final Library[] libs;

    UnloadLibraries(Library[] libs) {
      this.libs = libs;
    }

    @Override
    public void doIt(Project proj) {
      for (var i = libs.length - 1; i >= 0; i--) {
        proj.getLogisimFile().removeLibrary(libs[i]);
      }
    }

    @Override
    public String getName() {
      return (libs.length == 1) ? S.get("unloadLibraryAction") : S.get("unloadLibrariesAction");
    }

    @Override
    public void undo(Project proj) {
      for (final var lib : libs) {
        proj.getLogisimFile().addLibrary(lib);
      }
    }
  }

  private LogisimFileActions() {}

  static void addCircWithDeps(Circuit circ, LogisimFile mergelib, Set<Circuit> visited) {
    if (visited.contains(circ)) return;
    for (final var comp : circ.getNonWires()) {
      if (comp.getFactory() instanceof SubcircuitFactory subFact) {
        final var subCirc = subFact.getSubcircuit();
        if (mergelib.getCircuits().contains(subCirc)) {
          addCircWithDeps(subCirc, mergelib, visited);
        }
      }
    }
    visited.add(circ);
  }

  public static Set<Circuit> getCircuitDependencies(Circuit circ, LogisimFile mergelib) {
    final var visited = new java.util.LinkedHashSet<Circuit>();
    addCircWithDeps(circ, mergelib, visited);
    visited.remove(circ);
    return visited;
  }

  public static Action addVhdl(VhdlContent vhdl) {
    return new AddVhdl(vhdl);
  }

  public static Action addCircuit(Circuit circuit) {
    return new AddCircuit(circuit);
  }

  public static Action mergeFile(LogisimFile mergelib, LogisimFile source) {
    return mergeFile(mergelib, source, null, true);
  }

  public static Action mergeFile(LogisimFile mergelib, LogisimFile source, List<Circuit> selectedCircuits) {
    return mergeFile(mergelib, source, selectedCircuits, true);
  }

  /**
   * Builds the action merging circuits of {@code mergelib} into {@code source}, asking the user
   * about any conflict and any library file to locate.
   *
   * @return the action, or null when there is nothing to merge (conflicts made it impossible, or
   *     the user cancelled)
   */
  public static Action mergeFile(LogisimFile mergelib, LogisimFile source, List<Circuit> selectedCircuits, boolean includeDependencies) {
    return mergeFile(mergelib, source, selectedCircuits, includeDependencies, ASK_USER);
  }

  static Action mergeFile(
      LogisimFile mergelib,
      LogisimFile source,
      List<Circuit> selectedCircuits,
      boolean includeDependencies,
      MergeConflictResolver resolver) {
    final var action =
        new MergeFile(mergelib, source, selectedCircuits, includeDependencies, resolver);
    return action.isEmpty() ? null : action;
  }

  /**
   * Builds the action loading libraries into {@code source}.
   *
   * @return the action, or null when every library was rejected (the user has been told why), so
   *     that no empty step lands in the undo history
   */
  public static Action loadLibraries(Library[] libs, LogisimFile source) {
    final var action = new LoadLibraries(libs, source);
    return action.isEmpty() ? null : action;
  }

  /** Single-library form of {@link #loadLibraries}; may return null likewise. */
  public static Action loadLibrary(Library lib, LogisimFile source) {
    return loadLibraries(new Library[] {lib}, source);
  }

  public static Action moveCircuit(AddTool tool, int toIndex) {
    return new MoveCircuit(tool, toIndex);
  }

  /**
   * Renames a circuit.
   *
   * <p>There was no rename anywhere in the program. A circuit could only be renamed by clearing
   * the selection so the properties panel fell back to the circuit's own properties, then editing
   * the name row — which meant knowing that the panel does that.
   *
   * <p>Built on the same circuit mutation the properties row uses, so it is one undoable step and
   * the duplicate-name check in {@code LogisimFile.circuitChanged} still applies.
   */
  public static Action renameCircuit(Circuit circuit, String name) {
    final var mutation = new CircuitMutation(circuit);
    mutation.setForCircuit(CircuitAttributes.NAME_ATTR, name);
    return mutation.toAction(S.getter("renameCircuitAction"));
  }

  public static Action removeCircuit(Circuit circuit) {
    return new RemoveCircuit(circuit);
  }

  public static Action removeVhdl(VhdlContent vhdl) {
    return new RemoveVhdl(vhdl);
  }

  public static Action revertDefaults() {
    return new RevertDefaults();
  }

  public static Action setMainCircuit(Circuit circuit) {
    return new SetMainCircuit(circuit);
  }

  public static Action unloadLibraries(Library[] libs) {
    return new UnloadLibraries(libs);
  }

  public static Action unloadLibrary(Library lib) {
    return new UnloadLibraries(new Library[] {lib});
  }
}
