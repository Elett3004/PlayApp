package com.proyecto.PlayApp.service;

import java.math.BigDecimal;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;

@Service
public class PaymentService {
    private final String mpAccessToken;
    private final boolean useSandboxInitPoint;
    private final String playAppBaseUrl;

    public PaymentService(
            @Value("${playapp.payment.mp.access-token:}") String mpAccessToken,
            @Value("${playapp.payment.mp.use-sandbox-init-point:false}") boolean useSandboxInitPoint,
            @Value("${playapp.payment.base-url:http://localhost:8080}") String playAppBaseUrl) {
        this.mpAccessToken = mpAccessToken;
        this.useSandboxInitPoint = useSandboxInitPoint;
        this.playAppBaseUrl = normalizeBaseUrl(playAppBaseUrl);
    }

    public String crearPreferencia(String title, BigDecimal amount, int quantity, String ordenId)
        throws MPException, MPApiException {
        if (mpAccessToken == null || mpAccessToken.isBlank()) {
            throw new IllegalStateException("Falta MP_ACCESS_TOKEN para crear preferencias de pago.");
        }

        MercadoPagoConfig.setAccessToken(mpAccessToken);

        PreferenceItemRequest item =
                PreferenceItemRequest.builder()
                        .title(title)
                        .quantity(quantity)
                        .unitPrice(BigDecimal.valueOf(amount.longValue()))
                        .currencyId("COP")
                        .build();

        PreferenceRequest preferenceRequest;
        if (canUseMercadoPagoBackUrls(playAppBaseUrl)) {
            PreferenceBackUrlsRequest backUrls =
                    PreferenceBackUrlsRequest.builder()
                            .success(playAppBaseUrl + "/payment/proceed?estado=SUCCESS&orden=" + ordenId)
                            .pending(playAppBaseUrl + "/payment/proceed?estado=PENDING&orden=" + ordenId)
                            .failure(playAppBaseUrl + "/payment/proceed?estado=FAILED&orden=" + ordenId)
                            .build();

            preferenceRequest =
                    PreferenceRequest.builder()
                            .items(Collections.singletonList(item))
                            .backUrls(backUrls)
                            .autoReturn("approved")
                            .build();
        } else {
            preferenceRequest =
                    PreferenceRequest.builder()
                            .items(Collections.singletonList(item))
                            .build();
        }

        PreferenceClient client = new PreferenceClient();
        try {
            Preference preference = client.create(preferenceRequest);
            if (shouldUseSandboxInitPoint(preference)) {
                return preference.getSandboxInitPoint();
            }
            return preference.getInitPoint();
        } catch (MPApiException apiEx) {
            throw apiEx;

        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }

    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:8080";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private boolean canUseMercadoPagoBackUrls(String baseUrl) {
        String normalizedUrl = baseUrl.toLowerCase();
        return normalizedUrl.startsWith("https://")
                && !normalizedUrl.contains("localhost")
                && !normalizedUrl.contains("127.0.0.1");
    }

    private boolean shouldUseSandboxInitPoint(Preference preference) {
        return (useSandboxInitPoint || mpAccessToken.startsWith("TEST-"))
                && preference.getSandboxInitPoint() != null
                && !preference.getSandboxInitPoint().isBlank();
    }
}
