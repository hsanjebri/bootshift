package dev.bootshift.fixture.restclients;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Calls the greeting endpoint with a RestTemplate.
 *
 * <p>Uses three APIs that break in Boot 4 / Framework 7: {@code RestTemplateBuilder} moved to the
 * spring-boot-restclient module, {@code UriComponentsBuilder.fromHttpUrl} was removed, and
 * {@code getRawStatusCode()} was removed.
 */
@Component
public class GreetingRestClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public GreetingRestClient(RestTemplateBuilder builder, @Value("${greeting.base-url}") String baseUrl) {
        this.restTemplate = builder.build();
        this.baseUrl = baseUrl;
    }

    public String greet(String name) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/greeting")
                .queryParam("name", name)
                .build()
                .toUri();
        Greeting greeting = restTemplate.getForObject(uri, Greeting.class);
        return greeting == null ? "" : greeting.message();
    }

    /** Returns the HTTP status code of the call, including error codes. */
    public int status(String name) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/greeting")
                .queryParam("name", name)
                .build()
                .toUri();
        try {
            return restTemplate.getForEntity(uri, Greeting.class).getStatusCode().value();
        } catch (HttpStatusCodeException e) {
            return e.getRawStatusCode();
        }
    }
}
