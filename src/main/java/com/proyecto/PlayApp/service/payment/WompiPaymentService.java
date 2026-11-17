package com.proyecto.PlayApp.service.payment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.PlayApp.entity.Pedido;

@Service
public class WompiPaymentService implements PaymentGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(WompiPaymentService.class);

    private final String publicKey;
    private final String privateKey;
    private final String integritySecret;
    private final String eventsSecret;
    private final String sandboxUrl;
    private final String baseUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WompiPaymentService(
            @Value("${playapp.payment.wompi.public-key:}") String publicKey,
            @Value("${playapp.payment.wompi.private-key:}") String privateKey,
            @Value("${playapp.payment.wompi.integrity-secret:}") String integritySecret,
            @Value("${playapp.payment.wompi.events-secret:}") String eventsSecret,
            @Value("${playapp.payment.wompi.sandbox-url:https://sandbox.wompi.co/v1}") String sandboxUrl,
            @Value("${playapp.payment.base-url:http://localhost:8080}") String baseUrl) {
        this.publicKey = publicKey.trim();
        this.privateKey = privateKey.trim();
        this.integritySecret = integritySecret.trim();
        this.eventsSecret = eventsSecret.trim();
        this.sandboxUrl = (sandboxUrl == null || sandboxUrl.isBlank()) ? "https://sandbox.wompi.co/v1" : sandboxUrl.trim();
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8080" : baseUrl.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProveedorNombre() {
        return "WOMPI";
    }

    @Override
    public boolean estaDisponible() {
        return publicKey != null && !publicKey.isBlank() && publicKey.startsWith("pub_test_");
    }

    public java.util.List<String> obtenerVariablesFaltantes() {
        java.util.List<String> faltantes = new java.util.ArrayList<>();
        if (publicKey == null || publicKey.isBlank() || !publicKey.startsWith("pub_test_")) {
            faltantes.add("WOMPI_PUBLIC_KEY (debe comenzar por pub_test_)");
        }
        if (privateKey == null || privateKey.isBlank() || !privateKey.startsWith("prv_test_")) {
            faltantes.add("WOMPI_PRIVATE_KEY (debe comenzar por prv_test_)");
        }
        if (integritySecret == null || integritySecret.isBlank()) {
            faltantes.add("WOMPI_INTEGRITY_SECRET (secreto para calcular firma SHA-256)");
        }
        return faltantes;
    }

    @Override
    public PaymentInitResponse iniciarPago(Pedido pedido, double valor) {
        String ordenId = (pedido != null) ? pedido.getId() : "orden_demo";
        String referencia = "playapp_" + ordenId + "_" + System.currentTimeMillis();
        long montoEnCentavos = Math.round(valor * 100);
        String moneda = "COP";
        String signature = calcularFirmaIntegridad(referencia, montoEnCentavos, moneda, integritySecret);
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : "http://localhost:8080";
        String redirectUrl = base + "/payment/wompi/redirect";

        boolean configurado = estaDisponible() && (privateKey != null && privateKey.startsWith("prv_test_"));
        java.util.List<String> faltantes = obtenerVariablesFaltantes();

        return PaymentInitResponse.builder()
                .proveedor("WOMPI")
                .ordenId(ordenId)
                .referencia(referencia)
                .montoEnCentavos(montoEnCentavos)
                .valor(valor)
                .moneda(moneda)
                .widgetPublicKey(publicKey)
                .signatureIntegrity(signature)
                .redirectUrl(redirectUrl)
                .configurado(configurado)
                .variablesFaltantes(faltantes)
                .mensaje(configurado ? "Listo para checkout seguro Wompi Sandbox" : "Credenciales de Wompi Sandbox pendientes")
                .build();
    }

    @Override
    public PaymentVerificationResult verificarTransaccion(String transaccionId, String referencia) {
        if (transaccionId == null || transaccionId.isBlank()) {
            return PaymentVerificationResult.builder()
                    .transaccionId(transaccionId)
                    .referencia(referencia)
                    .status(PaymentStatus.ERROR)
                    .mensaje("ID de transacción vacío o no proporcionado por Wompi.")
                    .verificado(false)
                    .build();
        }

        try {
            String url = sandboxUrl.replaceAll("/+$", "") + "/transactions/" + transaccionId;
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(15));

            if (privateKey != null && !privateKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + privateKey);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.path("data");

                String statusStr = data.path("status").asText("");
                String ref = data.path("reference").asText(referencia);
                String metodo = data.path("payment_method_type").asText("Tarjeta/Wompi");
                double monto = data.path("amount_in_cents").asDouble(0) / 100.0;
                String moneda = data.path("currency").asText("COP");

                PaymentStatus status = mapStatus(statusStr);
                String ordenExtraida = extraerOrdenIdDeReferencia(ref);

                return PaymentVerificationResult.builder()
                        .ordenId(ordenExtraida)
                        .transaccionId(transaccionId)
                        .referencia(ref)
                        .status(status)
                        .metodoPago(metodo)
                        .valor(monto)
                        .moneda(moneda)
                        .mensaje("Transacción verificada exitosamente contra Wompi: " + statusStr)
                        .verificado(true)
                        .build();
            } else {
                logger.warn("Wompi respondió con código HTTP {} al verificar transacción {}", response.statusCode(), transaccionId);
                return PaymentVerificationResult.builder()
                        .transaccionId(transaccionId)
                        .referencia(referencia)
                        .status(PaymentStatus.ERROR)
                        .mensaje("No se pudo verificar la transacción con Wompi (HTTP " + response.statusCode() + ")")
                        .verificado(false)
                        .build();
            }

        } catch (Exception e) {
            logger.error("Error al consultar la API de Wompi para verificar transacción {}", transaccionId, e);
            return PaymentVerificationResult.builder()
                    .transaccionId(transaccionId)
                    .referencia(referencia)
                    .status(PaymentStatus.ERROR)
                    .mensaje("Error de conexión al verificar con Wompi: " + e.getMessage())
                    .verificado(false)
                    .build();
        }
    }

    public static String calcularFirmaIntegridad(String referencia, long montoCentavos, String moneda, String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        try {
            String cadena = referencia + montoCentavos + moneda + secret;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cadena.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("Error al calcular la firma de integridad SHA-256 de Wompi", e);
            return "";
        }
    }

    public static String extraerOrdenIdDeReferencia(String referencia) {
        if (referencia == null || !referencia.startsWith("playapp_")) {
            return null;
        }
        String sinPrefijo = referencia.substring("playapp_".length());
        int ultimoGuion = sinPrefijo.lastIndexOf('_');
        if (ultimoGuion > 0) {
            return sinPrefijo.substring(0, ultimoGuion);
        }
        return sinPrefijo;
    }

    private PaymentStatus mapStatus(String wompiStatus) {
        if (wompiStatus == null) return PaymentStatus.ERROR;
        return switch (wompiStatus.toUpperCase()) {
            case "APPROVED" -> PaymentStatus.APROBADO;
            case "DECLINED" -> PaymentStatus.RECHAZADO;
            case "VOIDED" -> PaymentStatus.CANCELADO;
            case "PENDING" -> PaymentStatus.PENDIENTE;
            default -> PaymentStatus.ERROR;
        };
    }
}
