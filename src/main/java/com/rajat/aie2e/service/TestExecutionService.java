package com.rajat.aie2e.service;

import com.rajat.aie2e.evaluation.EvaluationResult;
import com.rajat.aie2e.reporting.ReportGenerator;
import com.rajat.aie2e.runner.ConversationRunner;
import com.rajat.aie2e.scenarios.ScenarioRegistry;
import com.rajat.aie2e.scenarios.TestScenario;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class TestExecutionService {

    public String execute(MultipartFile file) throws Exception {

        Path tempFile =
                Files.createTempFile(
                        "scenario_",
                        ".xlsx"
                );

        file.transferTo(tempFile.toFile());

        List<TestScenario> scenarios =
                ScenarioRegistry.fromExcel(tempFile);

        List<EvaluationResult> results =
                new ArrayList<>();

        for (TestScenario scenario : scenarios) {

            ConversationRunner runner =
                    new ConversationRunner(scenario);

            results.add(runner.run());
        }

        Files.deleteIfExists(tempFile);

        return new ReportGenerator()
                .generateSummary(results);
    }
}