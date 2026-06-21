package com.rajat.aie2e.controller;

import com.rajat.aie2e.config.AppConfig;
import com.rajat.aie2e.dto.RunTestResponse;
import com.rajat.aie2e.service.TestExecutionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = "*")
@RestController
public class TestController {

    private final TestExecutionService testExecutionService;

    public TestController(TestExecutionService testExecutionService) {
        this.testExecutionService = testExecutionService;
    }

    @PostMapping(
            value = "/run",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public RunTestResponse runTest(
            @RequestParam("appId") String appId,
            @RequestParam("file") MultipartFile file
    ) throws Exception {

        AppConfig.setSprinklrAppId(appId);

        String reportPath =
                testExecutionService.execute(file);

        return new RunTestResponse(
                "SUCCESS",
                reportPath
        );
    }
}