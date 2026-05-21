package com.proyecto.PlayApp.dto;

public class ChatAction {
    private String type;
    private String label;
    private String url;

    public ChatAction() {
    }

    public ChatAction(String type, String label, String url) {
        this.type = type;
        this.label = label;
        this.url = url;
    }

    public static ChatAction link(String label, String url) {
        return new ChatAction("link", label, url);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
