package com.neueda.leap.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Client mistakes must come back as 4xx, not as 500 server errors.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void testUnknownPathIs404() throws Exception {
        mockMvc.perform(get("/no-such-path"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void testWrongMethodIs405() throws Exception {
        mockMvc.perform(post("/items/1"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void testMissingParameterIs400() throws Exception {
        mockMvc.perform(get("/search"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testWrongPathVariableTypeIs400() throws Exception {
        mockMvc.perform(get("/items/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Malformed request"));
    }

    @Test
    void testUnreadableBodyIs400() throws Exception {
        mockMvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testTradingRuleFailureIs422WithItsCode() throws Exception {
        mockMvc.perform(get("/broke-rule"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_CASH"))
            .andExpect(jsonPath("$.message").value("Not enough cash"));
    }

    @Test
    void testUnexpectedErrorIs500() throws Exception {
        mockMvc.perform(get("/boom"))
            .andExpect(status().isInternalServerError());
    }

    @RestController
    static class TestController {

        @GetMapping("/items/{id}")
        String getItem(@PathVariable("id") Integer id) {
            return "item " + id;
        }

        @PostMapping("/items")
        String createItem(@RequestBody Map<String, Object> body) {
            return "created";
        }

        @GetMapping("/search")
        String search(@RequestParam("q") String q) {
            return q;
        }

        @GetMapping("/broke-rule")
        String brokeRule() {
            throw new TradingRuleException("INSUFFICIENT_CASH", "Not enough cash");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("unexpected");
        }
    }
}
