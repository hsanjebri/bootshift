package dev.bootshift.fixture.securitychain;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApiController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void publicEndpointIsOpen() throws Exception {
        mvc.perform(get("/public/hello")).andExpect(status().isOk());
    }

    @Test
    void apiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void httpBasicLoginWorks() throws Exception {
        mvc.perform(get("/api/me").with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("user"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotReachAdmin() throws Exception {
        mvc.perform(get("/api/admin/stats")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReachAdmin() throws Exception {
        mvc.perform(get("/api/admin/stats")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void csrfIsDisabledForApiPosts() throws Exception {
        mvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value("hi"));
    }

    @Test
    @WithMockUser
    void unknownPathsAreDenied() throws Exception {
        mvc.perform(get("/internal/secret")).andExpect(status().isForbidden());
    }
}
