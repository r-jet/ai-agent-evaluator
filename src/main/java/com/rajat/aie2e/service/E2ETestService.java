package com.rajat.aie2e.service;

import com.rajat.aie2e.evaluation.EvaluationResult;
import com.rajat.aie2e.runner.ConversationRunner;
import com.rajat.aie2e.scenarios.ScenarioRegistry;
import com.rajat.aie2e.scenarios.TestScenario;

import java.util.ArrayList;
import java.util.List;

public class E2ETestService {

    public List<EvaluationResult> runAllScenarios() throws Exception {

        List<EvaluationResult> results = new ArrayList<>();

        for (TestScenario scenario : ScenarioRegistry.ALL_SCENARIOS) {

            ConversationRunner runner =
                    new ConversationRunner(scenario);

            results.add(runner.run());
        }

        return results;
    }
}