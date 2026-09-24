# Recovered QA assets

`original-workflow.js` is the exact prior inspector/verification/clustering workflow recovered from its tool call and matched against the original file. It requires the former workflow host and is retained as provenance, not claimed to run in the current Codex host.

`original-qa.sh.txt` and `original-session.sh.txt` preserve the original KWin/Xwayland harness. Do not run them unreviewed: the original runner uses absolute historical paths, recursively resets a session directory, does not isolate `user.home`, trusts stale PID files and composites captures onto a fixed 2560×1600 image. Replacement reviewers used unique sessions, isolated homes and scoped capture/cleanup. W0 in the main plan specifies the durable runner requirements.

`index.json` identifies every original agent assignment and whether a final result was recoverable. The original journal and transcripts remain under:

`/home/nilsb/.claude/projects/-home-nilsb-Documents-projects-logisim-revolution/aa97432b-04cd-4681-802b-fbefae8bfa3e/subagents/workflows/wf_2db45d7c-b0c/`

`recover.mjs` extracts completed reports, computed assignments, bounded partial tool notes and exact workflow source into `/tmp/logisim-qa-recovery-20260924`. It does not extract private model reasoning. It uses historical host paths and writes generated audit artifacts.

`assemble.mjs` consolidates the recovered reports and five replacement reviews, preserves original claims alongside their reviewed dispositions, archives referenced fresh evidence, and generates the finding register/checklist. Its historical input paths are intentional: run `recover.mjs` to recover the old inputs, then restore the five `reviewers/*.json`/`.md` files and the archived `lead-decisions.json`/`lead-new-findings.json` to the scratch directory before rerunning. It overwrites generated QA artifacts only; it does not edit application code. `verify-codeaudit.json` preserves the completed original verifier, including its 15 additional observations.

`ThemeLifecycleProbe.java` is a diagnostic source launcher outside the application source tree. Run against the inspected jar with isolated Java preferences and home:

```sh
java -Djava.awt.headless=true \
  -Duser.home=/tmp/logisim-theme-probe-home \
  -Djava.util.prefs.userRoot=/tmp/logisim-theme-probe-prefs \
  -cp build/libs/logisim-evolution-5.1.0dev-all.jar \
  docs/qa/2026-09-24/recovery/ThemeLifecycleProbe.java
```

Observed output is archived in `../evidence/theme-probe-result.txt`. The probe demonstrates shared icon mutation and an ineffective timeout; it is not a screenshot test or a measurement of a real desktop probe hanging.

Nine abandoned original verifier app/compositor sessions were stopped after validating their recorded PIDs and command identities. Their screenshots, logs, preferences and scratch circuit copies were preserved. The real desktop was not stopped.
