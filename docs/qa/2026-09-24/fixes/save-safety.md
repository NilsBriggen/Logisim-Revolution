# Save safety fixes — 2026-09-24

## Outcome / verification

Both source-confirmed close/save hazards are fixed. Parent reports `compileJava` and
`checkstyleMain` passed (16 seconds), followed by the 1,017-test regression checkpoint: the new
save-failure and extension-decision tests passed, with no failures in this worker's ownership.
This is parent-reported integration evidence; this worker ran no Gradle commands. Source and tests
are frozen. No real user preferences, circuits or autosaves were modified.

## Production changes

- `file/LogisimFile.java`: every write overload now propagates checked `IOException`. Transformer,
  parser and library failures cannot silently return success. A forwarding `LibraryLoader` records
  errors that XmlWriter reports without throwing (such as omitted/unresolved content), forwards the
  original error notification, and fails the completed write even if it produced nonempty XML.
  Autosave retains its non-blocking EDT error forwarding and retired-error suppression. The pipe
  writer catches the checked failure and closes its stream. Serialized IDs/formats are unchanged.
- `file/Loader.java`: save and autosave serialize into a same-directory temporary file, close it,
  require nonempty output, then publish with atomic replacement where supported. Serialization uses
  the final destination for relative library paths, not the temporary filename. Failed staging is
  removed and never replaces the existing destination/recovery.
- Normal Save copies an existing destination to an available backup only after staging succeeds.
  Failed publication attempts to restore from that backup while retaining the backup itself.
  Failure restores the loader's old main-file association and returns false without marking the
  project clean or deleting recovery. Name/library-save notifications and backup/recovery cleanup
  occur only after successful publication. Backup creation failure does not overwrite the target.
- Autosave changes its tracked recovery path and removes superseded recovery only after successful
  publication. A serializer failure leaves the previous recovery bytes intact.
- Loader export paths now return false on checked write failure; ZIP export restores its previous
  writer reference in `finally`, and directory export closes its output stream.
- `proj/ProjectActions.java`: the extension prompt accepts only explicit Replace/Add/Keep choices.
  Null, uninitialized, closed or unknown results return no destination, causing Save As to return
  false before overwrite/save. Parent's existing tool-restoration and Quit changes are preserved.

## Focused regression tests

- New `file/SaveFailureTest` — 10 cases: injected partial-output exception and serializer-reported
  missing-library error both preserve destination, existing backup, recovery, dirty state/name and
  loader association; failed Save As publishes no partial target; failed autosave preserves recovery;
  actual XmlWriter stream failure escapes after bytes were written; reported errors are forwarded
  and fail nonempty serialization; bundle export returns false/restores its writer; successful Save
  still publishes, cleans recovery and leaves no staging files.
- New `proj/SaveExtensionDecisionTest` — 2 cases: dismissal/unknown values yield no destination;
  explicit Replace/Add/Keep retain their intended filename behavior.
- Updated `file/LogisimFileAutosaveTest`: serializer-error fixtures now require `IOException` and
  return failure like the real Loader, retaining EDT delivery, retirement and no-deadlock assertions.

Tests use disabled-autosave fixtures where appropriate, recording/mock loaders, injected streams
and `@TempDir` destinations/recovery/backup sentinels. Existing caller compatibility was confirmed
by the parent's successful integrated production compilation and regression checkpoint.

Suggested selectors for later targeted reruns:

```text
--tests com.cburch.logisim.file.SaveFailureTest
--tests com.cburch.logisim.proj.SaveExtensionDecisionTest
--tests com.cburch.logisim.file.LogisimFileAutosaveTest
--tests com.cburch.logisim.file.ProjectBundleExportTest
--tests com.cburch.logisim.file.TextPersistenceTest
--tests com.cburch.logisim.file.VhdlAppearanceXmlTest
```

## Limitations

- No fresh native close -> failed-save or extension-dialog Esc/X permutation was run after these
  edits. Native acceptance remains parent-owned; decision and failure-preservation tests passed.
- Atomic replacement falls back to ordinary replacement when the filesystem does not support it.
  This is not an fsync/power-loss durability guarantee. Filesystem publication/rollback failures,
  unsupported atomic moves and real disk exhaustion were not induced in these tests. Normal Save
  retains its backup on failed publication/rollback; rollback errors are attached to the failure.
- Unremovable temporary files are logged rather than treated as successful saves. Existing
  successful-save backup/recovery deletion remains best effort.
- This is not a redesign of concurrent manual-save/autosave coordination or transactional bundle
  export: failed exports now report false but may leave a partial export artifact. General external
  writers and filesystem permission/symlink semantics were not newly certified.
- Forwarded serializer diagnostics may be followed by the transaction-level failure diagnostic;
  this does not permit success or recovery cleanup. No new localization keys were introduced.
