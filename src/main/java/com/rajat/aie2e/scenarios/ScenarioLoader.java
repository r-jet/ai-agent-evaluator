package com.rajat.aie2e.scenarios;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Loads TestScenarios from an Excel (.xlsx) file.
 *
 * Expected sheet structure (row 1 = header, row 2+ = data):
 *
 *   Name | Description | CustomerPersona | OpeningPrompt | MaxTurns | Evaluation Criteria
 *
 * Evaluation Criteria column:
 *   Each criterion is on its own line inside the cell (Alt+Enter in Excel),
 *   OR separated by a pipe character |, OR comma-separated.
 *   All three formats are handled automatically.
 *
 *   Example cell value (any format works):
 *     "Agent greeted the customer warmly"
 *     "Agent asked at least one clarifying question"
 *     "Agent provided a concrete next step"
 *
 * Blank rows are silently skipped.
 * If MaxTurns is missing or non-numeric, defaults to 5.
 */
public class ScenarioLoader {

    // ── Column indices (0-based, matching the header row) ────────────────────
    private static final int COL_NAME        = 0;
    private static final int COL_DESCRIPTION = 1;
    private static final int COL_PERSONA     = 2;
    private static final int COL_OPENING     = 3;
    private static final int COL_MAX_TURNS   = 4;
    private static final int COL_CRITERIA    = 5;

    /**
     * Read all scenarios from the given Excel file path.
     * Skips the header row (row index 0) and any blank rows.
     *
     * @param excelPath path to the .xlsx file
     * @return list of TestScenario, one per data row
     * @throws Exception if the file cannot be read or is structurally invalid
     */
    public static List<TestScenario> load(Path excelPath) throws Exception {
        List<TestScenario> scenarios = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(excelPath.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Validate header row exists
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException(
                        "Excel file appears empty — no header row found: " + excelPath);
            }

            int lastRow = sheet.getLastRowNum();
            System.out.println("[ScenarioLoader] Reading " + excelPath.getFileName()
                    + " — " + lastRow + " data row(s) found");

            // Start from row index 1 (skip header)
            for (int rowIdx = 1; rowIdx <= lastRow; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isRowBlank(row)) continue;

                try {
                    TestScenario scenario = parseRow(row, rowIdx + 1); // +1 for human-readable row num
                    scenarios.add(scenario);
                    System.out.println("[ScenarioLoader]   ✅ Row " + (rowIdx + 1)
                            + " → scenario: " + scenario.name());
                } catch (Exception e) {
                    System.err.println("[ScenarioLoader]   ⚠️  Row " + (rowIdx + 1)
                            + " skipped — " + e.getMessage());
                }
            }
        }

        if (scenarios.isEmpty()) {
            throw new IllegalStateException(
                    "No valid scenarios found in: " + excelPath
                            + "\nCheck that the file has data rows below the header.");
        }

        System.out.println("[ScenarioLoader] Loaded " + scenarios.size() + " scenario(s)");
        return scenarios;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static TestScenario parseRow(Row row, int humanRowNum) {
        String name        = requireCell(row, COL_NAME,        "Name",        humanRowNum);
        String description = requireCell(row, COL_DESCRIPTION, "Description", humanRowNum);
        String persona     = requireCell(row, COL_PERSONA,     "CustomerPersona", humanRowNum);
        String opening     = requireCell(row, COL_OPENING,     "OpeningPrompt",   humanRowNum);

        int maxTurns = 5; // default
        Cell turnsCell = row.getCell(COL_MAX_TURNS);
        if (turnsCell != null && turnsCell.getCellType() == CellType.NUMERIC) {
            maxTurns = (int) turnsCell.getNumericCellValue();
        } else if (turnsCell != null) {
            String turnsStr = cellText(turnsCell).trim();
            if (!turnsStr.isBlank()) {
                try { maxTurns = Integer.parseInt(turnsStr); }
                catch (NumberFormatException e) {
                    System.err.println("[ScenarioLoader]   ⚠️  Row " + humanRowNum
                            + ": MaxTurns '" + turnsStr + "' is not a number — defaulting to 5");
                }
            }
        }

        List<String> criteria = parseCriteria(row, humanRowNum);

        return new TestScenario(name.trim(), description.trim(),
                persona.trim(), opening.trim(), maxTurns, criteria);
    }

    /**
     * Parse the Evaluation Criteria cell.
     *
     * Supports three formats people naturally use in Excel:
     *   1. Each criterion on its own line (Alt+Enter line breaks inside one cell)
     *   2. Pipe-separated:  "Criterion A | Criterion B | Criterion C"
     *   3. Comma-separated (quoted or unquoted)
     *
     * Whichever delimiter is detected first (newline > pipe > comma) is used.
     * Leading/trailing whitespace and surrounding quotes are stripped per criterion.
     */
    private static List<String> parseCriteria(Row row, int humanRowNum) {
        Cell cell = row.getCell(COL_CRITERIA);
        if (cell == null) return List.of();

        String raw = cellText(cell).trim();
        if (raw.isBlank()) return List.of();

        String[] parts;

        if (raw.contains("\n")) {
            // Alt+Enter line breaks
            parts = raw.split("\\r?\\n");
        } else if (raw.contains("|")) {
            parts = raw.split("\\|");
        } else {
            // Comma-separated — may be quoted per criterion
            parts = raw.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        }

        return Arrays.stream(parts)
                .map(String::trim)
                .map(s -> s.replaceAll("^\"|\"$", "")) // strip surrounding quotes
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    private static String requireCell(Row row, int colIdx, String colName, int humanRowNum) {
        Cell cell = row.getCell(colIdx);
        String value = cell == null ? "" : cellText(cell).trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Required column '" + colName + "' is empty in row " + humanRowNum);
        }
        return value;
    }

    private static String cellText(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getRichStringCellValue().getString();
            default      -> "";
        };
    }

    private static boolean isRowBlank(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = cellText(cell).trim();
                if (!val.isBlank()) return false;
            }
        }
        return true;
    }
}