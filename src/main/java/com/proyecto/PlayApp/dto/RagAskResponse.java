package com.proyecto.PlayApp.dto;

import java.util.List;

public class RagAskResponse {
    private String answer;
    private List<String> sources;
    private List<String> retrievedChunks;
    private boolean success;
    private boolean contextFound;

    public RagAskResponse() {
    }

    public RagAskResponse(String answer, List<String> sources, List<String> retrievedChunks, boolean success, boolean contextFound) {
        this.answer = answer;
        this.sources = sources;
        this.retrievedChunks = retrievedChunks;
        this.success = success;
        this.contextFound = contextFound;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }

    public List<String> getRetrievedChunks() {
        return retrievedChunks;
    }

    public void setRetrievedChunks(List<String> retrievedChunks) {
        this.retrievedChunks = retrievedChunks;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isContextFound() {
        return contextFound;
    }

    public void setContextFound(boolean contextFound) {
        this.contextFound = contextFound;
    }
}
