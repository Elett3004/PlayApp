package com.proyecto.PlayApp.service;

import com.proyecto.PlayApp.model.RagDocumentChunk;
import com.proyecto.PlayApp.util.RagTextSimilarityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RagRetrievalService {
    private final RagDocumentLoaderService documentLoaderService;

    public List<RagDocumentChunk> retrieve(String question, int topK) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        return documentLoaderService.getChunks().stream()
                .map(chunk -> new RagDocumentChunk(
                        chunk.getId(),
                        chunk.getSource(),
                        chunk.getContent(),
                        RagTextSimilarityUtil.cosineSimilarity(question, chunk.getContent())
                ))
                .sorted(Comparator.comparingDouble(RagDocumentChunk::getScore).reversed())
                .limit(topK)
                .toList();
    }
}
