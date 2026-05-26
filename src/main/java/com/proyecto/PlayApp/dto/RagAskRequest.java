package com.proyecto.PlayApp.dto;

public class RagAskRequest {
    private String question;

    public RagAskRequest() {
    }

    public RagAskRequest(String question) {
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
