package dev.bootshift.fixture.restclients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Calls the greeting endpoint with a WebClient built from the auto-configured
 * {@code WebClient.Builder}. In Boot 4 that bean needs spring-boot-starter-webclient.
 */
@Component
public class GreetingWebClient {

    private final WebClient webClient;

    public GreetingWebClient(WebClient.Builder builder, @Value("${greeting.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public String greet(String name) {
        Greeting greeting = webClient.get()
                .uri(uri -> uri.path("/greeting").queryParam("name", name).build())
                .retrieve()
                .bodyToMono(Greeting.class)
                .block();
        return greeting == null ? "" : greeting.message();
    }
}
