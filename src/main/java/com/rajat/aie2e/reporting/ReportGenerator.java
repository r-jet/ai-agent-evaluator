package com.rajat.aie2e.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rajat.aie2e.config.AppConfig;
import com.rajat.aie2e.evaluation.EvaluationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**

 * Generates a summary report across all scenarios run in a single execution.
 *
 * Produces:
 * reports/summary_<timestamp>.txt   — human-readable summary table
 * reports/summary_<timestamp>.json  — machine-readable results for CI/CD
 */
public class ReportGenerator {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =

            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(ZoneId.systemDefault());

    private final ObjectMapper mapper = new ObjectMapper();

    public String generateSummary(List<EvaluationResult> results) throws IOException {

        if (results.isEmpty()) {
            System.out.println("⚠️  No results to summarise.");
            return null;
        }

        Path reportsDir = Path.of(AppConfig.REPORTS_DIR);
        Files.createDirectories(reportsDir);

        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        String baseName = "summary_" + timestamp;

        savePlainText(
                reportsDir.resolve(baseName + ".txt"),
                results
        );

        saveJson(
                reportsDir.resolve(baseName + ".json"),
                results
        );

        System.out.println(
                "\n📊 Summary report saved to: "
                        + reportsDir.toAbsolutePath()
                        + "/"
                        + baseName
                        + ".*"
        );

        return reportsDir
                .resolve(baseName + ".json")
                .toString();
    }

// ── Private ───────────────────────────────────────────────────────────

    private void savePlainText(
            Path path,
            List<EvaluationResult> results
    ) throws IOException {

        StringBuilder sb = new StringBuilder();

        sb.append("════════════════════════════════════════════════════════════\n");
        sb.append("E2E TEST SUITE SUMMARY\n");
        sb.append("Run at: ")
                .append(DISPLAY_FORMAT.format(Instant.now()))
                .append("\n");
        sb.append("Scenarios run: ")
                .append(results.size())
                .append("\n");
        sb.append("════════════════════════════════════════════════════════════\n\n");

        sb.append(String.format(
                "%-35s %8s %10s %10s%n",
                "Scenario",
                "Score",
                "Criteria",
                "Result"
        ));

        sb.append("─".repeat(65)).append("\n");

        int totalScore = 0;

        for (EvaluationResult r : results) {

            String outcome =
                    r.overallScore() >= 70 ? "PASS" : "FAIL";

            sb.append(String.format(
                    "%-35s %7d/100 %4d/%-5d %10s%n",
                    r.scenarioName(),
                    r.overallScore(),
                    r.passCount(),
                    r.criterionResults().size(),
                    outcome
            ));

            totalScore += r.overallScore();
        }

        sb.append("─".repeat(65)).append("\n");

        int avg = totalScore / results.size();

        long passing = results.stream()
                .filter(r -> r.overallScore() >= 70)
                .count();

        sb.append(String.format(
                "Average score: %d/100 | Scenarios passing (≥70): %d/%d%n",
                avg,
                passing,
                results.size()
        ));

        sb.append("\n\nDETAILED RESULTS\n");
        sb.append("═".repeat(60)).append("\n");

        for (EvaluationResult r : results) {
            sb.append(r.toDisplayString()).append("\n");
        }

        Files.writeString(path, sb.toString());
    }

    private void saveJson(
            Path path,
            List<EvaluationResult> results
    ) throws IOException {

        ObjectNode root = mapper.createObjectNode();

        root.put("generatedAt", Instant.now().toString());
        root.put("totalScenarios", results.size());

        int avg = results.stream()
                .mapToInt(EvaluationResult::overallScore)
                .sum() / results.size();

        root.put("averageScore", avg);

        long passing = results.stream()
                .filter(r -> r.overallScore() >= 70)
                .count();

        root.put("scenariosPassing", passing);
        root.put("passThreshold", 70);

        ArrayNode scenariosNode =
                root.putArray("scenarios");

        for (EvaluationResult r : results) {

            ObjectNode sNode =
                    scenariosNode.addObject();

            sNode.put("name", r.scenarioName());
            sNode.put("overallScore", r.overallScore());
            sNode.put("passed", r.overallScore() >= 70);
            sNode.put("passCount", r.passCount());
            sNode.put("totalCriteria",
                    r.criterionResults().size());
            sNode.put("summary", r.summary());
            sNode.put("concerns", r.concerns());

            ArrayNode crNode =
                    sNode.putArray("criterionResults");

            for (EvaluationResult.CriterionResult cr :
                    r.criterionResults()) {

                ObjectNode c =
                        crNode.addObject();

                c.put("criterion", cr.criterion());
                c.put("passed", cr.passed());
                c.put("explanation",
                        cr.explanation());
            }
        }

        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), root);
    }
}