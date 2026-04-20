package com.rajat.aie2e.livechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajat.aie2e.config.AppConfig;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Logs every outbound HTTP request and its response to:
 *   reports/requests.log
 *
 * Each entry is formatted as a curl command so you can paste it directly
 * into a terminal or compare it line-by-line with your Postman request.
 *
 * Usage: call logRequest() before sending, logResponse() after receiving.
 * The file is appended to (not overwritten) so a full multi-scenario run
 * produces one file with all calls in order.
 */
public class RequestLogger {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final PrintWriter writer;

    public RequestLogger() throws IOException {
        Path dir = Path.of(AppConfig.REPORTS_DIR);
        Files.createDirectories(dir);
        // append=true so multiple scenarios accumulate in one file per run
        this.writer = new PrintWriter(new FileWriter(dir.resolve("requests.log").toFile(), true));
        writeSeparator("SESSION START  " + Instant.now());
    }

    /**
     * Log a POST request as a curl command.
     *
     * @param url     Full URL
     * @param headers List of "Name: Value" strings (in the order they were set)
     * @param body    Raw request body string (will be pretty-printed if JSON)
     */
    public void logRequest(String method, String url, List<String> headers, String body) {
        writeSeparator("REQUEST  " + TS.format(Instant.now()) + "  " + method + " " + url);

        // ── curl equivalent ───────────────────────────────────────────────────
        writer.println("# curl equivalent:");
        writer.print("curl --location '" + url + "' \\\n");
        for (String h : headers) {
            writer.print("  --header '" + h + "' \\\n");
        }
        if (body != null && !body.isBlank()) {
            writer.println("  --data '" + body.replace("'", "'\"'\"'") + "'");
        }
        writer.println();

        // ── structured breakdown ──────────────────────────────────────────────
        writer.println("# Headers sent:");
        for (String h : headers) {
            writer.println("  " + h);
        }
        writer.println();

        writer.println("# Request body (raw):");
        writer.println(prettyPrintIfJson(body));
        writer.println();
        writer.flush();
    }

    /**
     * Log the response received for the most recent request.
     *
     * @param statusCode HTTP status code
     * @param body       Raw response body
     */
    public void logResponse(int statusCode, String body) {
        writer.println("# Response  HTTP " + statusCode + ":");
        writer.println(prettyPrintIfJson(body));
        writer.println();
        writer.flush();
    }

    public void close() {
        writeSeparator("SESSION END  " + Instant.now());
        writer.close();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void writeSeparator(String label) {
        writer.println();
        writer.println("═".repeat(70));
        writer.println("  " + label);
        writer.println("═".repeat(70));
        writer.flush();
    }

    private String prettyPrintIfJson(String text) {
        if (text == null || text.isBlank()) return "(empty)";
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return text;
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object parsed = mapper.readValue(trimmed, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            return text; // not valid JSON — return as-is
        }
    }
}