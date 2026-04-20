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
import java.util.ArrayList;
import java.util.List;

/**
 * Writes per-scenario logs to the reports/ directory.
 *
 * Produces two files per scenario run:
 *   reports/<scenario-name>_<timestamp>.txt   — human-readable transcript + evaluation
 *   reports/<scenario-name>_<timestamp>.json  — machine-readable structured data
 *
 * Files are never overwritten: each run gets a unique timestamp in the filename.
 */
public class ConversationLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private final ObjectMapper mapper = new ObjectMapper();
    private final String scenarioName;
    private final String runTimestamp;
    private final Instant startTime;

    private final List<LogEntry> entries = new ArrayList<>();
    private EvaluationResult evaluationResult;

    public ConversationLogger(String scenarioName) {
        this.scenarioName = scenarioName;
        this.startTime = Instant.now();
        this.runTimestamp = TIMESTAMP_FORMAT.format(startTime);
    }

    public void logCustomer(String text) {
        entries.add(new LogEntry("CUSTOMER", text, Instant.now()));
    }

    public void logAgent(String text) {
        entries.add(new LogEntry("AGENT", text, Instant.now()));
    }

    public void setEvaluationResult(EvaluationResult result) {
        this.evaluationResult = result;
    }

    public void save() throws IOException {
        Path reportsDir = Path.of(AppConfig.REPORTS_DIR);
        Files.createDirectories(reportsDir);

        String baseName = scenarioName + "_" + runTimestamp;

        savePlainText(reportsDir.resolve(baseName + ".txt"));
        saveJson(reportsDir.resolve(baseName + ".json"));

        System.out.println("\n📁 Logs saved to: " + reportsDir.toAbsolutePath() + "/" + baseName + ".*");
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void savePlainText(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("════════════════════════════════════════════════════════════\n");
        sb.append("E2E TEST RUN\n");
        sb.append("Scenario : ").append(scenarioName).append("\n");
        sb.append("Started  : ").append(DISPLAY_FORMAT.format(startTime)).append("\n");
        sb.append("════════════════════════════════════════════════════════════\n\n");

        sb.append("TRANSCRIPT\n");
        sb.append("──────────────────────────────────────────────────────────\n");

        for (LogEntry entry : entries) {
            sb.append("[").append(DISPLAY_FORMAT.format(entry.timestamp)).append("]\n");
            sb.append(entry.role).append(": ").append(entry.text).append("\n\n");
        }

        if (evaluationResult != null) {
            sb.append("\n").append(evaluationResult.toDisplayString());
        }

        Files.writeString(path, sb.toString());
    }

    private void saveJson(Path path) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("scenarioName", scenarioName);
        root.put("runTimestamp", runTimestamp);
        root.put("startedAt", startTime.toString());

        ArrayNode messagesNode = root.putArray("messages");
        for (LogEntry entry : entries) {
            ObjectNode msgNode = messagesNode.addObject();
            msgNode.put("role", entry.role);
            msgNode.put("text", entry.text);
            msgNode.put("timestamp", entry.timestamp.toString());
        }

        if (evaluationResult != null) {
            ObjectNode evalNode = root.putObject("evaluation");
            evalNode.put("overallScore", evaluationResult.overallScore());
            evalNode.put("passCount", evaluationResult.passCount());
            evalNode.put("totalCriteria", evaluationResult.criterionResults().size());
            evalNode.put("summary", evaluationResult.summary());
            evalNode.put("concerns", evaluationResult.concerns());

            ArrayNode criteriaNode = evalNode.putArray("criterionResults");
            for (EvaluationResult.CriterionResult cr : evaluationResult.criterionResults()) {
                ObjectNode crNode = criteriaNode.addObject();
                crNode.put("criterion", cr.criterion());
                crNode.put("passed", cr.passed());
                crNode.put("explanation", cr.explanation());
            }
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    private record LogEntry(String role, String text, Instant timestamp) {}
}