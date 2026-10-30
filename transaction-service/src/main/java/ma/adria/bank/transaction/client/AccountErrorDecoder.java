package ma.adria.bank.transaction.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import ma.adria.bank.transaction.domain.BusinessRuleException;
import ma.adria.bank.transaction.domain.ResourceNotFoundException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Traduit les réponses 4xx d'account-service en exceptions métier. Elles ne comptent donc
 * pas comme des pannes pour le circuit breaker. Les 5xx restent des FeignException.
 */
public class AccountErrorDecoder implements ErrorDecoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == 404) {
            return new ResourceNotFoundException(detail(response, "Compte introuvable"));
        }
        if (status == 400 || status == 409 || status == 422) {
            return new BusinessRuleException(detail(response, "Requête refusée par account-service"));
        }
        return defaultDecoder.decode(methodKey, response);
    }

    private static String detail(Response response, String fallback) {
        if (response.body() == null) {
            return fallback;
        }
        try (InputStream in = response.body().asInputStream()) {
            JsonNode detail = MAPPER.readTree(in).get("detail");
            return detail != null ? detail.asText() : fallback;
        } catch (IOException | RuntimeException e) {
            return fallback;
        }
    }
}
