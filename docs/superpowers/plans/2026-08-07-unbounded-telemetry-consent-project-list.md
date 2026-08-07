# Unbounded Telemetry Consent Project List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render and bind every registered telemetry project inside the consent page's existing scrolling viewport.

**Architecture:** Move the repeated project row markup into one reusable UI document. `TelemetryConsentPage` clears the row container, appends one document per view-model row, populates selectors by child index, and binds events against the matching project ID.

**Tech Stack:** Java 25, Hytale custom UI command/event builders, HYUIML `.ui` assets, JUnit 5, Gradle.

## Global Constraints

- Preserve project sorting, consent defaults, global toggles, save behavior, and scrolling behavior.
- Do not cap, paginate, or hide registered projects.
- Do not change telemetry collection or upload behavior; the privacy policy remains unchanged.
- Add only one behavior-level regression test for the former eight-row failure.

---

### Task 1: Dynamically Render Every Consent Project

**Files:**
- Create: `runtime/src/main/resources/Common/UI/Custom/TelemetryConsentProjectRow.ui`
- Modify: `runtime/src/main/resources/Common/UI/Custom/TelemetryConsentPage.ui`
- Modify: `runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentPage.java`
- Test: `runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentPageRenderingTest.java`

**Interfaces:**
- Consumes: `TelemetryConsentViewModel.ProjectRow`, `UICommandBuilder.append(String, String)`, `UICommandBuilder.clear(String)`, and indexed selectors such as `#TelemetryConsentRows[10]`.
- Produces: package-private `renderProjectRows(UICommandBuilder, UIEventBuilder, List<ProjectRow>)`, which appends, populates, and binds every project row.

- [ ] **Step 1: Write the failing rendering regression test**

Create eleven `ProjectRow` values, call `renderProjectRows`, and assert that commands contain one clear plus eleven appends of `TelemetryConsentProjectRow.ui`, including mutations for `#TelemetryConsentRows[10]`. Assert event bindings include the eleventh row's project toggle and project ID.

- [ ] **Step 2: Run the focused test and verify failure**

Run: `bash ../gradlew :alecstelemetry:runtime:test --tests com.alechilles.alecstelemetry.consent.TelemetryConsentPageRenderingTest`

Expected: compilation or assertion failure because `renderProjectRows` and the reusable row behavior do not yet exist.

- [ ] **Step 3: Extract the reusable row asset**

Move the current row-zero markup into `TelemetryConsentProjectRow.ui`. Import `Common.ui` and redeclare the three page-local styles used by the row (`TelemetryConsentUnsupportedCellLabel`, `TelemetryConsentInvisibleButtonStyle`, and tooltip styling only if the row references it). Leave `#TelemetryConsentRows` empty in the page shell and remove the overflow label.

- [ ] **Step 4: Implement dynamic row rendering and binding**

Add `PROJECT_ROW_UI_PATH = "TelemetryConsentProjectRow.ui"`. Replace `MAX_PROJECT_ROWS`, fixed visibility commands, and overflow commands with:

```java
commands.clear("#TelemetryConsentRows");
for (int index = 0; index < projects.size(); index++) {
    commands.append("#TelemetryConsentRows", PROJECT_ROW_UI_PATH);
    renderProject(commands, index, projects.get(index));
    bindProjectEvents(events, index, projects.get(index));
}
```

Change `rowSelector(index)` to return `#TelemetryConsentRows[" + index + "]`. Keep global bindings in `bindEvents` and delegate per-project bindings to the shared renderer so refreshes rebuild commands and bindings from the same snapshot.

- [ ] **Step 5: Run the focused test and verify success**

Run: `bash ../gradlew :alecstelemetry:runtime:test --tests com.alechilles.alecstelemetry.consent.TelemetryConsentPageRenderingTest`

Expected: PASS, with the eleventh row appended, populated, and bound.

- [ ] **Step 6: Validate UI grammar and package contents**

Validate both `.ui` documents with Hytale Workshop `validate_hytale_ui`. Then run:

```bash
bash ../gradlew :alecstelemetry:standalone:build
jar tf standalone/build/libs/alecstelemetry-standalone-1.1.0.jar | grep -E 'TelemetryConsent(Page|ProjectRow)\.ui'
```

Expected: clean build and both UI documents in the standalone artifact.

- [ ] **Step 7: Commit the implementation**

```bash
git add runtime/src/main/java/com/alechilles/alecstelemetry/consent/TelemetryConsentPage.java \
  runtime/src/main/resources/Common/UI/Custom/TelemetryConsentPage.ui \
  runtime/src/main/resources/Common/UI/Custom/TelemetryConsentProjectRow.ui \
  runtime/src/test/java/com/alechilles/alecstelemetry/consent/TelemetryConsentPageRenderingTest.java
git commit -m "Fix: render all telemetry consent projects"
```
