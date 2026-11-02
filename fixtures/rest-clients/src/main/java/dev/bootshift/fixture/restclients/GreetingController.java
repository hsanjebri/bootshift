package dev.bootshift.fixture.restclients;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    @GetMapping("/greeting")
    public ResponseEntity<Greeting> greeting(@RequestParam String name) {
        if (name.isBlank()) {
            // UNPROCESSABLE_ENTITY is deprecated in Spring Framework 7 in favour of UNPROCESSABLE_CONTENT.
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
        return ResponseEntity.ok(new Greeting("Hello, " + name + "!"));
    }
}
