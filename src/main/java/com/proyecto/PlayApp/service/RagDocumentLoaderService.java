package com.proyecto.PlayApp.service;

import com.proyecto.PlayApp.model.RagDocumentChunk;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class RagDocumentLoaderService {
    private static final int MAX_CHUNK_LENGTH = 700;
    private final List<RagDocumentChunk> chunks = new ArrayList<>();

    @PostConstruct
    public void loadDocuments() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:/rag/*.txt");
            int counter = 1;

            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                String content = readResource(resource);
                for (String part : splitIntoChunks(content, MAX_CHUNK_LENGTH)) {
                    chunks.add(new RagDocumentChunk("rag-chunk-" + counter, fileName, part));
                    counter++;
                }
            }

            log.info("Documentos RAG de PlayApp cargados. Fragmentos: {}", chunks.size());
        } catch (Exception ex) {
            throw new IllegalStateException("Error cargando documentos RAG de PlayApp", ex);
        }
    }

    public List<RagDocumentChunk> getChunks() {
        return Collections.unmodifiableList(chunks);
    }

    private String readResource(Resource resource) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private List<String> splitIntoChunks(String text, int maxLength) {
        List<String> result = new ArrayList<>();
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String cleanParagraph = paragraph.trim();
            if (cleanParagraph.isBlank()) {
                continue;
            }
            if (current.length() + cleanParagraph.length() > maxLength && !current.isEmpty()) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(cleanParagraph).append("\n\n");
        }

        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result;
    }
}
