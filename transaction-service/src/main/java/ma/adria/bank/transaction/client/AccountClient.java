package ma.adria.bank.transaction.client;

import ma.adria.bank.transaction.domain.AccountDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * Client OpenFeign vers account-service. L'URL est configurable ; elle désignera le nom
 * logique du service lorsque la découverte Eureka sera en place.
 */
@FeignClient(name = "account-service", url = "${bank.clients.account-service.url}")
public interface AccountClient {

    @GetMapping("/api/accounts/{id}")
    AccountDto getAccount(@PathVariable("id") UUID id);

    @PatchMapping("/api/accounts/{id}/balance")
    AccountDto applyBalanceDelta(@PathVariable("id") UUID id, @RequestBody BalanceDeltaRequest request);

    record BalanceDeltaRequest(double delta) {
    }
}
