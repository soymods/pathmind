package com.pathmind.ai;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Explicitly opt-in paid benchmark. Never accesses the player's stored credentials. */
public final class AiBehaviorEvalRunner {
    public static void main(String[] args) throws Exception {
        var cases = AiBehaviorEvalCase.load();
        int limit = Integer.parseInt(System.getProperty("aiEvalLimit", "5"));
        String difficulty = System.getProperty("aiEvalDifficulty", "");
        String caseIds = System.getProperty("aiEvalCases", "");
        var selected = select(cases, difficulty, caseIds, limit);
        int repeats = Integer.parseInt(System.getProperty("aiEvalRepeats", "1"));
        if (repeats < 1 || repeats > 10) throw new IllegalArgumentException("Repeats must be 1–10.");
        if (!Boolean.getBoolean("aiEvalLive")) {
            System.out.println("AI eval corpus: " + cases.size() + " requests. Offline mode; no API calls.");
            System.out.println("Selected: " + selected.stream().map(AiBehaviorEvalCase::id).toList() + "; repeats=" + repeats);
            System.out.println("This command validates selection only. Run ./gradlew test for scripted regressions; it is not a provider quality result.");
            return;
        }
        var type = AiProviderType.valueOf(System.getProperty("aiEvalProvider", "OPENAI"));
        String model = System.getProperty("aiEvalModel", "");
        if (model.isBlank()) throw new IllegalArgumentException("Set aiEvalModel explicitly for a live benchmark.");
        String env = switch (type) {
            case OPENAI -> "OPENAI_API_KEY";
            case ANTHROPIC -> "ANTHROPIC_API_KEY";
            case GEMINI -> "GEMINI_API_KEY";
            case OPENROUTER -> "OPENROUTER_API_KEY";
            case OPENAI_COMPATIBLE -> "AI_EVAL_API_KEY";
        };
        String key = System.getenv(env);
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Missing " + env + ".");
        String endpoint = System.getProperty("aiEvalEndpoint", type.defaultEndpoint());
        AiProvider provider = switch (type) {
            case OPENAI -> new OpenAiResponsesProvider(endpoint, key);
            case ANTHROPIC -> new AnthropicProvider(endpoint, key);
            case GEMINI -> new GeminiProvider(endpoint, key);
            // Pinning a sort keeps a benchmark run from silently comparing different upstream hosts.
            case OPENROUTER -> new OpenRouterProvider(endpoint, key, System.getProperty("aiEvalRoutingSort", ""), true);
            case OPENAI_COMPATIBLE -> new OpenAiCompatibleProvider(endpoint, key);
        };
        AiRunMetrics.AiPricing pricing = null;
        if (System.getProperty("aiEvalPricing") != null) {
            String[] rates = System.getProperty("aiEvalPricing").split(",");
            if (rates.length != 4) throw new IllegalArgumentException("Pricing needs input,cached,cacheWrite,output rates per million.");
            pricing = new AiRunMetrics.AiPricing(Double.parseDouble(rates[0]), Double.parseDouble(rates[1]),
                Double.parseDouble(rates[2]), Double.parseDouble(rates[3]));
        }
        Path output = Path.of(System.getProperty("aiEvalOutput", "build/reports/ai-evals"), "run-" + System.currentTimeMillis() + ".json");
        Files.createDirectories(output.getParent());
        var gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        JsonObject report = new JsonObject();
        report.addProperty("corpusVersion", 2);
        report.addProperty("evidence", "live-provider structural grading; no Minecraft world execution");
        report.addProperty("repeats", repeats);
        report.add("selectedCaseIds", gson.toJsonTree(selected.stream().map(AiBehaviorEvalCase::id).toList()));
        report.addProperty("provider", provider.providerId());
        report.addProperty("model", model);
        JsonArray results = new JsonArray();
        report.add("results", results);
        List<Long> latencies = new ArrayList<>();
        int valid = 0, behavior = 0, passed = 0;
        long turns = 0, repairs = 0;
        for (var test : selected) {
          for (int repeat = 1; repeat <= repeats; repeat++) {
            var graph = test.activeGraph();
            var context = AiWorkspaceContext.attach(test.context().toString(), "Eval fixture", graph, java.util.List.of());
            var run = AiPresetAgent.runMeasured(provider, model, test.prompt(), context, graph, "Eval fixture", true, true).join();
            var grade = AiBehaviorEvalGrader.gradeRun(test, run);
            JsonObject result = new JsonObject();
            result.addProperty("id", test.id());
            result.addProperty("repeat", repeat);
            result.addProperty("difficulty", test.difficulty());
            result.add("grade", gson.toJsonTree(grade));
            result.add("metrics", gson.toJsonTree(run.metrics()));
            result.add("toolTrace", gson.toJsonTree(run.trace()));
            // Provider errors may contain operator-controlled URLs; do not persist raw errors.
            result.addProperty("requestFailed", !run.succeeded());
            result.add("estimatedCost", gson.toJsonTree(run.metrics().estimatedCost(pricing)));
            results.add(result);
            if (grade.validationPassed()) valid++;
            if (grade.behaviorPresent()) behavior++;
            if (grade.passed()) passed++;
            turns += run.metrics().toolTurns(); repairs += run.metrics().repairAttempts();
            latencies.add(run.metrics().latencyMillis());
            latencies.sort(Long::compare);
            JsonObject summary = new JsonObject();
            int n = results.size();
            summary.addProperty("completed", n);
            summary.addProperty("validationRate", (double) valid / n);
            summary.addProperty("behaviorRate", (double) behavior / n);
            summary.addProperty("passRate", (double) passed / n);
            summary.addProperty("meanToolTurns", (double) turns / n);
            summary.addProperty("meanRepairAttempts", (double) repairs / n);
            summary.addProperty("latencyP50Millis", latencies.get((int) Math.ceil(n * .50) - 1));
            summary.addProperty("latencyP95Millis", latencies.get((int) Math.ceil(n * .95) - 1));
            report.add("summary", summary);
            Files.writeString(output, gson.toJson(report));
            System.out.println(test.id() + ": " + (grade.passed() ? "PASS" : "FAIL"));
          }
        }
        System.out.println("Report: " + output.toAbsolutePath());
    }
    static List<AiBehaviorEvalCase> select(List<AiBehaviorEvalCase> cases, String difficulty, String caseIds, int limit) {
        if (limit < 1 || limit > cases.size()) throw new IllegalArgumentException("Limit must be 1–" + cases.size());
        var ids = new java.util.LinkedHashSet<String>();
        if (!caseIds.isBlank()) for (var id : caseIds.split(",")) ids.add(id.strip());
        for (var id : ids) if (cases.stream().noneMatch(test -> test.id().equals(id))) throw new IllegalArgumentException("Unknown case: " + id);
        var selected = cases.stream().filter(test -> difficulty.isBlank() || test.difficulty().equals(difficulty))
            .filter(test -> ids.isEmpty() || ids.contains(test.id())).limit(limit).toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("No matching cases.");
        return selected;
    }
}
