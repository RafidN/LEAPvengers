package com.neueda.leap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ExchangeRateService {
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "CAD", "AUD");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;

    public ExchangeRateService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${leap.exchange-rates.api-key:}") String apiKey,
            @Value("${leap.exchange-rates.api-url:https://allratestoday.com/api/v1/rates}") String apiUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
    }

    public ExchangeRate getRate(String source, String target) {
        String normalizedSource = normalize(source);
        String normalizedTarget = normalize(target);
        validateCurrency(normalizedSource);
        validateCurrency(normalizedTarget);

        if (normalizedSource.equals(normalizedTarget)) {
            return new ExchangeRate(normalizedSource, normalizedTarget, BigDecimal.ONE, Instant.now());
        }
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Exchange-rate provider is not configured");
        }

        String body = restClient.get()
                .uri(UriComponentsBuilder.fromUriString(apiUrl)
                        .queryParam("source", normalizedSource)
                        .queryParam("target", normalizedTarget)
                        .build()
                        .toUri())
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode rateNode = root.path("rate");
            if (rateNode.isMissingNode()) {
                rateNode = root.path("data").path("rate");
            }
            if (!rateNode.isNumber() && !rateNode.isTextual()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Exchange-rate provider returned no rate");
            }
            return new ExchangeRate(normalizedSource, normalizedTarget, new BigDecimal(rateNode.asText()), Instant.now());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid exchange-rate provider response", exception);
        }
    }

    public Map<String, ExchangeRate> getRates(String source) {
        String normalizedSource = normalize(source);
        validateCurrency(normalizedSource);
        Map<String, ExchangeRate> rates = new LinkedHashMap<>();
        for (String target : SUPPORTED_CURRENCIES) {
            rates.put(target, getRate(normalizedSource, target));
        }
        return rates;
    }

    private String normalize(String currency) {
        return currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
    }

    private void validateCurrency(String currency) {
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported currency: " + currency);
        }
    }

    public record ExchangeRate(String source, String target, BigDecimal rate, Instant fetchedAt) {}
}
