package com.proyecto.PlayApp.model;

public class RagDocumentChunk {
    private String id;
    private String source;
    private String content;
    private double score;

    public RagDocumentChunk() {
    }

    public RagDocumentChunk(String id, String source, String content) {
        this.id = id;
        this.source = source;
        this.content = content;
    }

    public RagDocumentChunk(String id, String source, String content, double score) {
        this.id = id;
        this.source = source;
        this.content = content;
        this.score = score;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
