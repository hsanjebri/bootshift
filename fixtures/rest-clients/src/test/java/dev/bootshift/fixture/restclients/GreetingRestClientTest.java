package dev.bootshift.fixture.restclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(components = GreetingRestClient.class, properties = "greeting.base-url=http://greeting.test")
class GreetingRestClientTest {

    @Autowired
    private GreetingRestClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void readsGreeting() {
        server.expect(requestTo("http://greeting.test/greeting?name=Ada"))
                .andRespond(withSuccess("{\"message\":\"Hello, Ada!\"}", MediaType.APPLICATION_JSON));

        assertThat(client.greet("Ada")).isEqualTo("Hello, Ada!");
    }

    @Test
    void reportsErrorStatus() {
        server.expect(requestTo("http://greeting.test/greeting?name=%20"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThat(client.status(" ")).isEqualTo(422);
    }
}
