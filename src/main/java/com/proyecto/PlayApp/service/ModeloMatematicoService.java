package com.proyecto.PlayApp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ModeloMatematicoService {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper objectMapper;

    public Map<String, Object> resolver(Map<String, Object> parametros) {
        Path script = null;
        try {
            script = copiarScriptTemporal();
            Process process = crearProceso(script);

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                objectMapper.writeValue(writer, parametros);
            }

            boolean terminado = process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            String salida = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            if (!terminado) {
                process.destroyForcibly();
                throw new IllegalStateException("El modelo matematico tardo demasiado en responder.");
            }

            if (process.exitValue() != 0) {
                throw new IllegalStateException(error.isBlank() ? "No fue posible resolver el modelo matematico." : error);
            }

            return objectMapper.readValue(salida, Map.class);
        } catch (IOException e) {
            throw new IllegalStateException("No se encontro Python, Pyomo o GLPK. Revisa que esten instalados.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La ejecucion del modelo fue interrumpida.", e);
        } finally {
            if (script != null) {
                try {
                    Files.deleteIfExists(script);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private Process crearProceso(Path script) throws IOException {
        for (String python : posiblesPython()) {
            try {
                return new ProcessBuilder(python, script.toString()).start();
            } catch (IOException ignored) {
            }
        }
        throw new IOException("No se encontro ejecutable de Python.");
    }

    private List<String> posiblesPython() {
        String configurado = System.getenv("PYTHON_EXECUTABLE");
        if (configurado != null && !configurado.isBlank()) {
            return List.of(configurado, "python3", "python");
        }
        return List.of("python3", "python");
    }

    private Path copiarScriptTemporal() throws IOException {
        ClassPathResource resource = new ClassPathResource("python/modelo_playapp.py");
        Path script = Files.createTempFile("modelo-playapp-", ".py");
        try (InputStream input = resource.getInputStream()) {
            Files.copy(input, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return script;
    }
}
