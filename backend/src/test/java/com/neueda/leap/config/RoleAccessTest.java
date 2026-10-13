package com.neueda.leap.config;

import com.neueda.leap.model.Users;
import com.neueda.leap.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the role rules in SecurityConfig through the real filter chain (S1.6).
 * Every request here is turned away before it reaches a controller, so no seed data is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    private String bearer(String role, Integer clientId) {
        Users user = new Users();
        user.setUserId(9999);
        user.setUsername("role-test-" + role.toLowerCase());
        user.setClientId(clientId);
        user.setRole(role);
        return "Bearer " + jwtUtil.generateToken(user);
    }

    @Test
    void traderIsForbiddenFromInternalPages() throws Exception {
        String trader = bearer("TRADER", 1);
        mockMvc.perform(get("/internal/audit/orders").header("Authorization", trader))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/reports/activity").header("Authorization", trader))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsAndAnalystAreForbiddenFromTraderEndpoints() throws Exception {
        for (String role : new String[] {"OPS", "ANALYST"}) {
            String token = bearer(role, null);
            mockMvc.perform(get("/accounts").header("Authorization", token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/orders").header("Authorization", token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/order/placed").header("Authorization", token)
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void analystIsForbiddenFromAudit() throws Exception {
        mockMvc.perform(get("/internal/audit/orders").header("Authorization", bearer("ANALYST", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/audit/orders")).andExpect(status().isUnauthorized());
    }
}
