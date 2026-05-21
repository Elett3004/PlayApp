package com.proyecto.PlayApp.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ChatHistoryResponse {
    private String sessionId;
    private List<ChatHistoryMessage> messages;

    public ChatHistoryResponse() {
    }

    public ChatHistoryResponse(String sessionId, List<ChatHistoryMessage> messages) {
        this.sessionId = sessionId;
        this.messages = messages;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<ChatHistoryMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatHistoryMessage> messages) {
        this.messages = messages;
    }

    public static class ChatHistoryMessage {
        private String role;
        private String content;
        private LocalDateTime timestamp;
        private List<ChatAction> actions;

        public ChatHistoryMessage() {
        }

        public ChatHistoryMessage(String role, String content, LocalDateTime timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }

        public ChatHistoryMessage(String role, String content, LocalDateTime timestamp, List<ChatAction> actions) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
            this.actions = actions;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
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
}
