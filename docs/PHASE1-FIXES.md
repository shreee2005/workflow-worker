# Phase 1 Fixes — DAG + Parallelism

**What this document covers:** Three bugs we found and fixed in the workflow engine.  
Each section explains the problem in plain English, shows a before/after example, and says what files changed.

---

## Quick Summary

| # | Problem | Severity | Files changed |
|---|---|---|---|
| 1 | `dependsOn` was buried inside executor config | Design debt | `StepDefinition`, `WorkflowSpecParser`, `DagParser` |
| 2 | Parallel steps shared an unsafe map → data corruption | Critical bug | `WorkflowContext` |
| 3 | Old linear execution logic was dead code | Misleading clutter | `WorkflowRunStepService` |

---

## Fix 1 — `dependsOn` was buried inside executor config

### What was wrong

A workflow step has two concerns:
1. **Control** — which other steps must finish before this one can start (`dependsOn`)
2. **Execution** — what the step actually does (its `url`, `method`, `body`, etc.)

Before this fix, both were crammed into the same `config` map:

```json
{
  "type": "HTTP_CALL",
  "config": {
    "url": "https://api.example.com/transform",
    "method": "POST",
    "dependsOn": [0]
  }
}
```

This caused three problems:

**Problem A — Executors saw workflow control fields**  
When `HttpCallStepExecutor` received its config, it found `dependsOn: [0]` sitting next to `url` and `method`. The executor has no use for it — it's noise that could confuse future executors or accidentally get sent as part of an HTTP body.

**Problem B — The API layer had no obvious contract**  
If someone building the API layer wanted to declare that step 2 depends on step 0, where do they put it? Inside `config`? At the top level of the step? There was no clear answer because `dependsOn` had no official home.

**Problem C — `StepDefinition` was blind to its own dependencies**  
The Java object representing a step had no `dependsOn` field. To find a step's dependencies, code had to reach into `getConfig().get("dependsOn")` — a fragile string lookup with no type safety.

---

### What we changed

**`StepDefinition.java` — added a proper field:**

```java
// Before: no dependsOn field at all
public class StepDefinition {
    private String type;
    private String name;
    private Map<String, Object> config;
}

// After: dependsOn is a first-class citizen
public class StepDefinition {
    private String type;
    private String name;
    private Map<String, Object> config;
    private List<Integer> dependsOn = Collections.emptyList(); // ← new
}
```

**`WorkflowSpecParser.java` — reads dependsOn cleanly, then strips it from config:**

```java
// 1. Try to read dependsOn from the step level (new preferred format)
List<Integer> dependsOn = parseDependsOn(node.get("dependsOn"));

// 2. If not found there, fall back to inside config (old format still works)
if (dependsOn.isEmpty()) {
    dependsOn = parseDependsOn(config.containsKey("dependsOn")
            ? mapper.convertValue(config.get("dependsOn"), JsonNode.class)
            : null);
}

// 3. Remove it from config so executors never see it
config.remove("dependsOn");
def.setDependsOn(dependsOn);
```

> **Backward compatibility:** Both JSON formats are accepted. Old workflow specs already stored in your database will continue to work — the parser checks the config map as a fallback.

**`DagParser.java` — reads the field directly, no more config digging:**

```java
// Before: brittle string lookup inside config
Object dependsOnObj = step.getConfig().get("dependsOn");

// After: clean, typed field access
for (int depIndex : step.getDependsOn()) {
    // ...
}
```

**New preferred JSON format:**

```json
{
  "type": "HTTP_CALL",
  "name": "transform_data",
  "dependsOn": [0],
  "config": {
    "url": "https://api.example.com/transform",
    "method": "POST",
    "body": "${steps[0].output}"
  }
}
```

`dependsOn` lives at the step level. `config` contains only what the executor needs.

---

## Fix 2 — Parallel steps shared an unsafe map

### What was wrong

When a stage runs multiple steps in parallel, each step runs on a different thread (via `CompletableFuture`). When a step finishes, it writes its output into a shared `WorkflowContext` object:

```java
context.setStepOutput(stepIndex, result.getOutput());
```

The problem: `WorkflowContext` stored those outputs in a plain `HashMap`. `HashMap` is **not thread-safe** — it was never designed for concurrent writes.

**What can go wrong with a plain HashMap under concurrent writes:**

```
Thread A (step 1) → writes output at key 1
Thread B (step 2) → writes output at key 2
                 ↕ (simultaneous)
HashMap internal structure gets corrupted
```

The consequences range from silent (an output just disappears, so a downstream step gets an empty value) to loud (a `ConcurrentModificationException` crash). This would only show up under real parallel load, not in unit tests, making it the worst kind of bug.

**Concrete example of the silent failure:**

Imagine this workflow: Step 0 → Steps 1 & 2 in parallel → Step 3

```
Step 1 fetches user data   → output: {"name": "Alice"}
Step 2 fetches post count  → output: {"count": 42}
Step 3 logs both           → message: "${steps[1].output.name} has ${steps[2].output.count} posts"
```

With a corrupt HashMap, step 3 might see step 1's output as null and produce:  
`" has 42 posts"` — no name, no error, just wrong data.

---

### What we changed

**`WorkflowContext.java`** — one-line change, two maps:

```java
// Before: crashes or corrupts data under parallel writes
this.stepOutputs = new HashMap<>();
this.variables  = new HashMap<>();

// After: safe for simultaneous reads and writes from multiple threads
this.stepOutputs = new ConcurrentHashMap<>();
this.variables  = new ConcurrentHashMap<>();
```

`ConcurrentHashMap` uses internal partitioning so two threads writing to different keys never block each other and never corrupt the structure. It's a drop-in replacement — the rest of the code didn't need to change.

---

## Fix 3 — Old linear execution logic was dead code

### What was wrong

Before the DAG executor was built, workflow execution was linear: run step 0, then step 1, then step 2, always in index order. That was implemented as `getNextPendingStepIndex()`:

```java
// Old approach: find the highest succeeded step, run the next one
int maxSucceeded = existing.stream()
    .filter(s -> s.getStatus() == SUCCEEDED)
    .map(WorkflowRunStep::getStepIndex)
    .max(...)
    .orElse(-1);

return maxSucceeded + 1;
```

This method still existed in `WorkflowRunStepService`. Nothing called it — `WorkflowExecutor` already used `findFirstIncompleteStage()` which understands stages and parallel steps. But it sat there quietly, 30 lines of misleading code.

**Why it's a problem even if unused:**

- A developer reading `WorkflowRunStepService` would reasonably think this is how the executor decides what to run next — and build on top of it
- It directly contradicts the DAG model: `maxSucceeded + 1` assumes steps always run in order 0, 1, 2, 3... which is exactly what the DAG system was built to move away from
- If someone ever accidentally calls it during a parallel run, it would return the wrong step

### What we changed

Deleted `getNextPendingStepIndex()` entirely from `WorkflowRunStepService`. The correct resume logic lives in `WorkflowExecutor.findFirstIncompleteStage()`, which checks which stages have all their steps completed and resumes from the first incomplete one.

---

## Tests

All 24 existing tests pass. Four tests in `DagParserTest` and `ParallelExecutionUnitTest` were updated as part of Fix 1 — they were constructing `StepDefinition` objects directly and putting `dependsOn` inside the config map. They now use `setDependsOn()` instead:

```java
// Before (in tests)
Map<String, Object> config = new HashMap<>();
config.put("dependsOn", List.of(0));
StepDefinition step = new StepDefinition("HTTP_CALL", config);

// After (in tests)
StepDefinition step = new StepDefinition("HTTP_CALL", Collections.emptyMap());
step.setDependsOn(List.of(0));
```

Tests that use `WorkflowSpecParser.parse()` with JSON didn't need to change — the parser handles both the old and new formats automatically.

---

## What's Next (Phase 2)

The engine now correctly plans and executes DAG workflows with parallelism. The next gap is **recovery**: if the worker crashes mid-execution, there is no checkpoint to resume from. The workflow would need to restart from scratch, risking duplicate work or data loss.

Phase 2 adds:
- A `WorkflowState` table that snapshots execution context after each stage
- Checkpoint restore logic in the consumer so a restarted worker picks up from the last saved stage
