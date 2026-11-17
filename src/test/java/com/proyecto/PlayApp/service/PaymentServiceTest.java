package com.proyecto.PlayApp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.proyecto.PlayApp.entity.Pedido;
import com.proyecto.PlayApp.service.payment.DemoPaymentService;
import com.proyecto.PlayApp.service.payment.PaymentGatewayManager;
import com.proyecto.PlayApp.service.payment.PaymentInitResponse;
import com.proyecto.PlayApp.service.payment.PaymentStatus;
import com.proyecto.PlayApp.service.payment.PaymentVerificationResult;
import com.proyecto.PlayApp.service.payment.WompiPaymentService;

public class PaymentServiceTest {

    @Test
    void testCalculoFirmaIntegridadWompi() {
        String referencia = "orden_12345";
        long montoCentavos = 2450000L;
        String moneda = "COP";
        String secreto = "test_integrity_secret_123";

        String firma = WompiPaymentService.calcularFirmaIntegridad(referencia, montoCentavos, moneda, secreto);

        assertNotNull(firma);
        assertFalse(firma.isBlank());
        assertEquals(64, firma.length()); // SHA-256 produce 64 caracteres hexadecimales
    }

    @Test
    void testWompiIniciarPago() {
        WompiPaymentService wompi = new WompiPaymentService(
                "pub_test_1234567890",
                "prv_test_0987654321",
                "secret_integ_abc",
                "secret_events_xyz",
                "https://sandbox.wompi.co/v1",
                "http://localhost:8080"
        );

        assertTrue(wompi.estaDisponible());

        Pedido pedido = Pedido.builder().id("orden_test_001").total(24500.0).build();
        PaymentInitResponse init = wompi.iniciarPago(pedido, 24500.0);

        assertNotNull(init);
        assertEquals("WOMPI", init.getProveedor());
        assertEquals("orden_test_001", init.getOrdenId());
        assertEquals(2450000L, init.getMontoEnCentavos());
        assertEquals("COP", init.getMoneda());
        assertEquals("pub_test_1234567890", init.getWidgetPublicKey());
        assertFalse(init.getSignatureIntegrity().isBlank());
        assertTrue(init.getRedirectUrl().contains("/payment/wompi/redirect"));
        assertTrue(init.isConfigurado());
    }

    @Test
    void testExtraerOrdenIdDeReferencia() {
        String ref = "playapp_orden_abc_123_1731800000";
        String ordenId = WompiPaymentService.extraerOrdenIdDeReferencia(ref);
        assertEquals("orden_abc_123", ordenId);
    }

    @Test
    void testWompiVariablesFaltantes() {
        WompiPaymentService wompiSinLlaves = new WompiPaymentService("", "", "", "", "", "http://localhost:8080");
        assertFalse(wompiSinLlaves.estaDisponible());
        java.util.List<String> faltantes = wompiSinLlaves.obtenerVariablesFaltantes();
        assertEquals(3, faltantes.size());
    }

    @Test
    void testDemoPaymentService() {
        DemoPaymentService demo = new DemoPaymentService();
        assertTrue(demo.estaDisponible());

        Pedido pedido = Pedido.builder().id("orden_demo_002").total(18000.0).build();
        PaymentInitResponse init = demo.iniciarPago(pedido, 18000.0);

        assertNotNull(init);
        assertEquals("DEMO", init.getProveedor());
        assertTrue(init.isConfigurado());

        // Probar verificación simulada aprobada
        PaymentVerificationResult resAprobado = demo.verificarTransaccion("demo_aprobado_123", "ref_001");
        assertEquals(PaymentStatus.APROBADO, resAprobado.getStatus());

        // Probar verificación simulada rechazada
        PaymentVerificationResult resRechazado = demo.verificarTransaccion("demo_declined_123", "ref_002");
        assertEquals(PaymentStatus.RECHAZADO, resRechazado.getStatus());

        // Probar verificación simulada cancelada
        PaymentVerificationResult resCancelado = demo.verificarTransaccion("demo_canceled_123", "ref_003");
        assertEquals(PaymentStatus.CANCELADO, resCancelado.getStatus());
    }

    @Test
    void testPaymentGatewayManagerFallbackDemo() {
        WompiPaymentService wompiSinLlaves = new WompiPaymentService("", "", "", "", "", "http://localhost:8080");
        DemoPaymentService demo = new DemoPaymentService();

        PaymentGatewayManager manager = new PaymentGatewayManager(wompiSinLlaves, demo);

        // Sin llaves de Wompi debe usar automáticamente el modo Demo
        assertEquals("DEMO", manager.getService("WOMPI").getProveedorNombre());
        assertEquals("DEMO", manager.getService("DEMO").getProveedorNombre());

        Pedido pedido = Pedido.builder().id("orden_fallback").total(35000.0).build();
        PaymentInitResponse init = manager.prepararPago(pedido, 35000.0, "WOMPI");
        assertFalse(init.isConfigurado());
        assertEquals(3, init.getVariablesFaltantes().size());
    }
}
