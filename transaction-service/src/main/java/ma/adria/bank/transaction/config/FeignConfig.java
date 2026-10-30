package ma.adria.bank.transaction.config;

import feign.codec.ErrorDecoder;
import ma.adria.bank.transaction.client.AccountErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    ErrorDecoder accountErrorDecoder() {
        return new AccountErrorDecoder();
    }
}
