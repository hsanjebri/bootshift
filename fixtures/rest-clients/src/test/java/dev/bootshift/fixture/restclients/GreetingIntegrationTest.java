package dev.bootshift.fixture.restclients;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Runs the real server on a random port. In Boot 4, TestRestTemplate moves to
 * spring-boot-resttestclient and needs {@code @AutoConfigureTestRestTemplate}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GreetingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Test
    void testRestTemplateReadsGreeting() {
        ResponseEntity<Greeting> response = testRestTemplate.getForEntity("/greeting?name=Grace", Greeting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new Greeting("Hello, Grace!"));
    }

    @Test
    void blankNameIsUnprocessable() {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/greeting?name= ", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void webClientReadsGreeting() {
        GreetingWebClient client = new GreetingWebClient(webClientBuilder, "http://localhost:" + port);

        assertThat(client.greet("Linus")).isEqualTo("Hello, Linus!");
    }
}
