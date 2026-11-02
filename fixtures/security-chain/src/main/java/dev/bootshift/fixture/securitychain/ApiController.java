package dev.bootshift.fixture.securitychain;

import java.security.Principal;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    @GetMapping("/public/hello")
    public Map<String, String> hello() {
        return Map.of("message", "hello");
    }

    @GetMapping("/api/me")
    public Map<String, String> me(Principal principal) {
        return Map.of("user", principal.getName());
    }

    @PostMapping("/api/notes")
    public Map<String, String> note(@RequestBody Map<String, String> body) {
        return Map.of("saved", body.getOrDefault("text", ""));
    }

    @GetMapping("/api/admin/stats")
    public Map<String, Integer> stats() {
        return Map.of("users", 2);
    }
}
