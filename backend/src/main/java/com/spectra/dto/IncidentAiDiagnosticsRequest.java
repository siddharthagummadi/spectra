package com.spectra.dto;

public class IncidentAiDiagnosticsRequest {

    private boolean forceReanalyze = false;

    public IncidentAiDiagnosticsRequest() {
    }

    public IncidentAiDiagnosticsRequest(boolean forceReanalyze) {
        this.forceReanalyze = forceReanalyze;
    }

    public boolean isForceReanalyze() {
        return forceReanalyze;
    }

    public void setForceReanalyze(boolean forceReanalyze) {
        this.forceReanalyze = forceReanalyze;
    }
}
