package com.rajat.aie2e;

import com.rajat.aie2e.config.AppConfig;
import com.rajat.aie2e.evaluation.EvaluationResult;
import com.rajat.aie2e.reporting.ReportGenerator;
import com.rajat.aie2e.runner.ConversationRunner;
import com.rajat.aie2e.scenarios.ScenarioRegistry;
import com.rajat.aie2e.scenarios.TestScenario;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Entry point.
 *
 * At startup the user is asked:
 *   1. Sprinklr App ID (if not set as env var)
 *   2. Scenario source: built-in hardcoded scenarios OR an Excel file
 *      (chosen via a native file picker dialog — no path typing needed)
 *
 * ── Running with a CLI argument (non-interactive / CI use) ───────────────
 *   java -jar app.jar --scenarios /path/to/scenarios.xlsx
 *
 * ── Skipping the scenario prompt entirely ────────────────────────────────
 *   Set env var:  SCENARIOS_FILE=/path/to/scenarios.xlsx
 */
public class Main {

    public static void main(String[] args) {

        // Required on macOS so Swing dialogs don't steal focus from IntelliJ
        System.setProperty("apple.awt.UIElement", "true");

        printBanner();

        // ── Step 1: Resolve Sprinklr App ID ───────────────────────────────────
        AppConfig.promptForAppIdIfNeeded();
        System.out.println();

        // ── Step 2: Resolve scenario source ───────────────────────────────────
        List<TestScenario> scenariosToRun = resolveScenarios(args);
        System.out.println();

        // ── Step 3: Run scenarios ──────────────────────────────────────────────
        List<EvaluationResult> allResults = new ArrayList<>();

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.printf( "║  Scenarios to run: %-38s║%n", scenariosToRun.size());
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        for (int i = 0; i < scenariosToRun.size(); i++) {
            TestScenario scenario = scenariosToRun.get(i);
            System.out.println("\n[" + (i + 1) + "/" + scenariosToRun.size() + "] Running: " + scenario.name());

            try {
                ConversationRunner runner = new ConversationRunner(scenario);
                EvaluationResult result = runner.run();
                allResults.add(result);
            } catch (Exception e) {
                System.err.println("❌ Scenario '" + scenario.name() + "' failed:");
                e.printStackTrace();
                allResults.add(new EvaluationResult(
                        scenario.name(), 0,
                        List.of(new EvaluationResult.CriterionResult(
                                "Scenario execution", false,
                                "Exception: " + e.getMessage()
                        )),
                        "Scenario failed to complete due to an exception.",
                        e.getClass().getSimpleName() + ": " + e.getMessage()
                ));
            }

            if (i < scenariosToRun.size() - 1) {
                try {
                    System.out.println("\n⏸  Pausing 3s before next scenario...");
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // ── Step 4: Summary report ─────────────────────────────────────────────
        try {
            new ReportGenerator().generateSummary(allResults);
        } catch (Exception e) {
            System.err.println("⚠️  Failed to generate summary report: " + e.getMessage());
        }

        printFinalSummary(allResults);
    }

    // ── Scenario resolution ───────────────────────────────────────────────────

    /**
     * Determine which scenarios to run, in this priority order:
     *   1. --scenarios <path> CLI argument
     *   2. SCENARIOS_FILE environment variable
     *   3. Interactive console prompt → file picker dialog if Excel chosen
     */
    private static List<TestScenario> resolveScenarios(String[] args) {

        // Priority 1: CLI argument
        String cliPath = parseScenariosArg(args);
        if (cliPath != null) {
            return loadFromExcelOrFallback(Path.of(cliPath), "CLI argument --scenarios");
        }

        // Priority 2: Environment variable
        String envPath = System.getenv("SCENARIOS_FILE");
        if (envPath != null && !envPath.isBlank()) {
            System.out.println("📂 SCENARIOS_FILE env var set: " + envPath);
            return loadFromExcelOrFallback(Path.of(envPath), "env var SCENARIOS_FILE");
        }

        // Priority 3: Interactive prompt
        return promptForScenarioSource();
    }

    private static List<TestScenario> promptForScenarioSource() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("┌─────────────────────────────────────────────────┐");
        System.out.println("│  Scenario source                                │");
        System.out.println("├─────────────────────────────────────────────────┤");
        System.out.println("│  [1] Use built-in scenarios (default)           │");
        System.out.println("│  [2] Load from Excel file (.xlsx)               │");
        System.out.println("└─────────────────────────────────────────────────┘");
        System.out.print("Choice [1/2]: ");
        System.out.flush();

        String choice = scanner.nextLine().trim();

        if ("2".equals(choice)) {
            Path chosen = openFilePicker();
            if (chosen == null) {
                System.out.println("⚠️  No file selected. Falling back to built-in scenarios.");
                return ScenarioRegistry.ALL_SCENARIOS;
            }
            return loadFromExcelOrFallback(chosen, "file picker");
        }

        // Default: built-in
        System.out.println("✅ Using built-in scenarios (" + ScenarioRegistry.ALL_SCENARIOS.size() + " total)");
        return ScenarioRegistry.ALL_SCENARIOS;
    }

    /**
     * Opens a native macOS/Windows/Linux file chooser dialog filtered to .xlsx files.
     * Returns the selected Path, or null if the user cancelled.
     */
    private static Path openFilePicker() {
        try {
            // Use the system look-and-feel so the dialog looks native on macOS
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Falls back to default Swing look-and-feel — still works fine
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Scenarios Excel File");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));

        // Open in the user's home directory by default
        chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        System.out.println("📂 Opening file picker — select your .xlsx scenarios file...");

        // Must run on the Event Dispatch Thread and wait for result
        int[] result = {JFileChooser.CANCEL_OPTION};
        try {
            SwingUtilities.invokeAndWait(() ->
                    result[0] = chooser.showOpenDialog(null)
            );
        } catch (Exception e) {
            System.err.println("⚠️  File picker failed: " + e.getMessage()
                    + "\n    Falling back to built-in scenarios.");
            return null;
        }

        if (result[0] == JFileChooser.APPROVE_OPTION) {
            Path selected = chooser.getSelectedFile().toPath();
            System.out.println("✅ Selected: " + selected.toAbsolutePath());
            return selected;
        }

        return null; // user cancelled
    }

    private static List<TestScenario> loadFromExcelOrFallback(Path path, String source) {
        try {
            List<TestScenario> scenarios = ScenarioRegistry.fromExcel(path);
            System.out.println("✅ Loaded " + scenarios.size()
                    + " scenario(s) from Excel [" + source + "]: " + path.toAbsolutePath());
            return scenarios;
        } catch (Exception e) {
            System.err.println("❌ Failed to load scenarios from Excel [" + source + "]: " + e.getMessage());
            System.err.println("   Falling back to built-in scenarios.");
            return ScenarioRegistry.ALL_SCENARIOS;
        }
    }

    private static String parseScenariosArg(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--scenarios".equals(args[i])) return args[i + 1];
        }
        return null;
    }

    // ── Display helpers ───────────────────────────────────────────────────────

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║        Sprinklr AI Agent E2E Test Suite                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printFinalSummary(List<EvaluationResult> allResults) {
        System.out.println("\n\n╔══════════════════════════════════════════════════════════╗");
        System.out.println(  "║                    FINAL SUMMARY                         ║");
        System.out.println(  "╠══════════════════════════════════════════════════════════╣");
        for (EvaluationResult r : allResults) {
            String icon = r.overallScore() >= 70 ? "✅" : "❌";
            System.out.printf("║  %s  %-38s  %3d/100  ║%n",
                    icon, r.scenarioName(), r.overallScore());
        }
        System.out.println(  "╚══════════════════════════════════════════════════════════╝");
    }
}