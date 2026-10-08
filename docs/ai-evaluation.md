# AI evaluation and provider sessions

The versioned corpus has 68 requests: 20 simple, 20 medium, 20 complex, and eight
lifecycle regressions for the reported workflow and conversation/context risks.
It covers actions, repeats, conditions, branches, fork/join, parameter cards,
variables/lists, routines/arguments, current-preset editing, and inspection.
Examples and corpus cases are structural patterns, not prompt-to-preset routing rules.

## Running

Offline, without credentials or network requests:

```sh
./gradlew :common:aiEval
```

Live runs incur provider charges. Supply an environment key (`OPENAI_API_KEY`,
`ANTHROPIC_API_KEY`, `GEMINI_API_KEY`, `OPENROUTER_API_KEY`, or `AI_EVAL_API_KEY` for
the compatible provider).
The harness does not read the player's encrypted keys. Set a model explicitly:

```sh
./gradlew :common:aiEval -PaiEvalLive=true -PaiEvalProvider=OPENAI \
  -PaiEvalModel=YOUR_MODEL -PaiEvalLimit=5
```

Provider values: `OPENAI`, `ANTHROPIC`, `GEMINI`, `OPENROUTER`, `OPENAI_COMPATIBLE`.
For `OPENROUTER`, `-PaiEvalRoutingSort=throughput|price|latency` pins upstream selection so
repeated runs are not silently compared across different hosts.
Optional `-PaiEvalDifficulty=simple|medium|complex|regression` filters before the limit.
Increase `-PaiEvalLimit=68` to run the entire corpus. Repeat separately for each model
and difficulty; reports identify both provider and model. A filtered run is a sample,
not a full-corpus result. `-PaiEvalEndpoint=URL` overrides the endpoint.
Use `-PaiEvalCases=lifecycle-position-return` or `-PaiEvalCases=lifecycle-quantity-edit`
to select exact case IDs (comma-separated).
Unknown IDs/empty filters fail explicitly. `-PaiEvalRepeats=3` repeats each case with
fresh fixtures/sessions (1–10). Reports identify every repetition and corpus version 2.
Offline aiEval checks corpus/selection only, not model quality; use tests for scripted regressions.
Select models supporting native tools (and strict schemas for OpenAI/Anthropic).
Existing saved model choices are preserved; an old/retired model may need updating.

Reports are saved incrementally under `common/build/reports/ai-evals` (override with
`-PaiEvalOutput=PATH`). They contain case IDs, structural failure descriptions,
numeric usage, and per-run validation/behavior/pass rates, mean tool/repair turns,
and p50/p95 latency. They exclude keys, raw provider reasoning, and conversation text.
Use difficulty-filtered runs to compare equivalent provider/model workloads.

Optional `-PaiEvalPricing=INPUT,CACHED,CACHE_WRITE,OUTPUT` supplies rates per million
tokens in the operator's chosen currency. Pricing is not inferred from model names.
Missing usage/caching data remains `null`, not zero; cost is unknown when required
token counters or pricing are missing. Compare prices using current provider rates.

## What is graded

The native validator grades graph validity. A separate structural grader checks
reachable required nodes, parameter values, modes, attachment/flow/socket relations,
routine signatures, message text, unexpected/disconnected nodes, and node budgets.
Inspection cases check response concepts and absence of graph changes.
Lifecycle grading adds outcome, response length, preserved fixture semantics/connections,
ordered flow, shared variable bindings, and required successful inspection tools.
The quantity-edit case requires the existing workflow and attached item to remain,
while the Craft amount changes from four to eight. This is evaluation data, not
a node-specific production instruction.
The reported workflow preserves the old five-second Walk → Jump, then captures Self XYZ,
walks five **blocks**, and returns through the same saved variable. Valid-but-wrong
seconds, variable, and action-order mutations must fail behavioral grading.
These checks do not simulate Minecraft, prove termination, or establish that every
semantically equivalent graph passes. Review failures before changing expectations.
In-game navigation, inventory, timing, and world-dependent behavior still need testing.

## Native providers and privacy

OpenAI now uses Responses API native function calls with narrowed per-tool schemas.
Only the old official default Chat Completions URL is migrated automatically;
custom URLs are left alone. Generic OpenAI-compatible endpoints retain the existing
JSON-envelope adapter rather than assuming Responses support.

Anthropic uses native tool-use/result blocks. Gemini uses function calls/responses.
OpenRouter uses Chat Completions `tool_calls` with one `role:"tool"` message per call, and
replays assistant turns verbatim so upstream `reasoning_details` survive the round trip. Its
Responses-shaped endpoint is deliberately not used: it is alpha, rejects `store` and
`previous_response_id`, and covers far fewer models than Chat Completions.
Opaque reasoning/signature output is preserved unchanged in request-scoped histories.
Parallel calls are rejected without graph edits, and results are paired with native
call IDs before recovery. Local history is bounded, never silently truncated.

OpenAI defaults to `store:false` and local native-history replay. The saved provider
setting `storeConversation:true` explicitly opts into server-side response chaining;
it is not enabled automatically or exposed as a UI toggle yet. Local replay still
retransmits native history; server chaining sends only new tool results. Anthropic
and Gemini replay native histories because this adapter does not create stored sessions.

Stable tool/instruction prefixes support caching: OpenAI has a stable cache key;
Anthropic marks static tools/system blocks with ephemeral cache breakpoints.
Gemini relies on provider implicit caching, not creation of paid explicit cache resources.
Cache hits are not guaranteed. See [OpenAI conversation state](https://developers.openai.com/api/docs/guides/conversation-state),
[Claude strict tools](https://platform.claude.com/docs/en/agents-and-tools/tool-use/strict-tool-use),
and [Gemini function declarations](https://ai.google.dev/api/generate-content#FunctionDeclaration).

Production requests record numeric telemetry in a bounded in-memory ring of 100 runs.
Failed requests also report metrics; each run has a four-minute budget in addition
to the existing tool-turn/progress limits. No persistent player telemetry upload exists.
The local chat archive now retains application-authored tool detail with turn number,
error code, and draft revision until the user resets that provider's chat. It does
not save provider reasoning, raw tool arguments, API keys, or full request bodies.

### User chat archive

User-visible chat is separate from request-scoped native tool histories. Each provider
has its own locally persisted archive at `<Minecraft directory>/pathmind/ai-chat-history.json`.
Messages and apply/discard events survive editor recreation and game restart until
the user resets that provider's chat with the upper-right trash icon (click twice).
Reset drops pending proposals and invalidates delayed callbacks; it does not edit the
live preset, remove provider credentials, or delete provider-side records.

The archive is **unencrypted local chat text**, like other local app data. Do not put
secrets in prompts. API key fields, native reasoning/tool histories, and isolated
draft graphs are not stored here. An unreadable file is preserved, not overwritten;
an explicit reset first moves it to a timestamped `.unreadable-*` backup.

The full visible archive is retained, but model input uses a recent-history window
of at most 24,000 characters, with individual long messages shortened to 8,000.
Omitted older entries are explicitly marked. Display-only work logs and diagnostics
are excluded from model context. There is no separate AI-authored summary or persistent
preferences layer; see [request lifecycle](ai-request-lifecycle.md) for context rules.
The current prompt is sent once, and fresh live-preset inspection remains
authoritative; historical proposals do not imply that edits were accepted.

## Model selection and remaining evidence-dependent work

The saved model selector is the single source of truth. Pathmind does not silently
route requests to another model based on prompt or graph complexity. Supported
GPT-5.4/5.5 requests set low text verbosity; Pathmind independently enforces a concise
reply budget unless the user explicitly asks for detail.

Run matched live benchmarks before changing the default or recommended model. Compare
graph pass rate, tool turns, latency, and cost across identical cases and repeated
runs. Offline tests make no measured provider-quality claim.

See [Pass 4 verification](ai-pass-4-verification.md) for targeted benchmarks, offline
evidence, and outstanding world/UI checks.
