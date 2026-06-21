package com.rajat.aie2e.dto;

public class RunTestResponse {

    private String status;
    private String reportPath;

    public RunTestResponse() {}

    public RunTestResponse(String status, String reportPath) {
        this.status = status;
        this.reportPath = reportPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }
}