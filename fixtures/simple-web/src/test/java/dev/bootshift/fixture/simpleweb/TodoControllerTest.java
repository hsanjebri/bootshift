package dev.bootshift.fixture.simpleweb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TodoController.class)
class TodoControllerTest {

    @Autowired
    private MockMvc mvc;

    // Deprecated in Boot 3.4, removed in Boot 4. The recipe should switch it to @MockitoBean.
    @MockBean
    private TodoService service;

    @Test
    void listsTodos() throws Exception {
        given(service.findAll()).willReturn(List.of(new Todo(1L, "write fixture", false)));

        mvc.perform(get("/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("write fixture"));
    }

    @Test
    void returnsNotFoundForMissingTodo() throws Exception {
        given(service.findById(42L)).willReturn(Optional.empty());

        mvc.perform(get("/todos/42")).andExpect(status().isNotFound());
    }

    @Test
    void createsTodo() throws Exception {
        given(service.create(any())).willReturn(new Todo(7L, "ship it", false));

        mvc.perform(post("/todos").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"ship it\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        mvc.perform(post("/todos").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
