package com.proyecto.PlayApp.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ChatSendResponse {
    private String sessionId;
    private String reply;
    private LocalDateTime timestamp;
    private List<ChatAction> actions;

    public ChatSendResponse() {
    }

    public ChatSendResponse(String sessionId, String reply, LocalDateTime timestamp) {
        this.sessionId = sessionId;
        this.reply = reply;
        this.timestamp = timestamp;
    }

    public ChatSendResponse(String sessionId, String reply, LocalDateTime timestamp, List<ChatAction> actions) {
        this.sessionId = sessionId;
        this.reply = reply;
        this.timestamp = timestamp;
        this.actions = actions;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<ChatAction> getActions() {
        return actions;
    }

    public void setActions(List<ChatAction> actions) {
        this.actions = actions;
    }
}
