package com.neueda.leap.controller;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.model.dto.AuditClientDetailsResponse;
import com.neueda.leap.model.dto.AuditClientSummary;
import com.neueda.leap.model.dto.AuditOrder;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.AuditRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.OrderEventsRepository;
import com.neueda.leap.security.JwtAuthenticationFilter;
import com.neueda.leap.security.JwtAuthenticationEntryPoint;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.AuditService;
import com.neueda.leap.service.ClientSegmentService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuditControllerSecurityTest.TestConfig.class})
class AuditControllerSecurityTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @WithMockUser(roles = "OPS")
        void opsCanSearchClients() throws Exception {
                mockMvc.perform(get("/internal/audit/clients").param("query", "ada"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].clientId").value(7))
                                .andExpect(jsonPath("$[0].segment").value("Premier"));
        }

        @Test
        @WithMockUser(roles = "OPS")
        void opsCanViewClientDetails() throws Exception {
                mockMvc.perform(get("/internal/audit/clients/7"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.clientId").value(7))
                                .andExpect(jsonPath("$.email").value("ada@example.com"));
        }

        @Test
        @WithMockUser(roles = "ANALYST")
        void analystGetsForbiddenForClientDetails() throws Exception {
                mockMvc.perform(get("/internal/audit/clients/7"))
                                .andExpect(status().isForbidden());
        }

        @TestConfiguration
        static class TestConfig {

                @Bean
                AuditService auditService() {
                        return new AuditService(auditRepository(), clientRepository(), accountRepository(), orderEventsRepository(),
                                        Mockito.mock(ClientSegmentService.class)) {
                                @Override
                                public List<AuditClientSummary> searchClients(String query) {
                                        return List.of(new AuditClientSummary(7, "Ada", "Lovelace", "ada@example.com", "Premier"));
                                }

                                @Override
                                public AuditClientDetailsResponse getClientDetails(Integer clientId) {
                                        return new AuditClientDetailsResponse(
                                                        clientId,
                                                        "Ada",
                                                        "Lovelace",
                                                        "ada@example.com",
                                                        "Premier",
                                                        List.of(),
                                                        List.<AuditOrder>of());
                                }
                        };
                }

                @Bean
                JwtAuthenticationFilter jwtAuthenticationFilter() {
                        return new JwtAuthenticationFilter(jwtUtil());
                }

                @Bean
                JwtUtil jwtUtil() {
                        return new JwtUtil();
                }

                @Bean
                JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
                        return new JwtAuthenticationEntryPoint();
                }

                @Bean
                AuditRepository auditRepository() {
                        return Mockito.mock(AuditRepository.class);
                }

                @Bean
                ClientRepository clientRepository() {
                        return Mockito.mock(ClientRepository.class);
                }

                @Bean
                AccountRepository accountRepository() {
                        return Mockito.mock(AccountRepository.class);
                }

                @Bean
                OrderEventsRepository orderEventsRepository() {
                        return Mockito.mock(OrderEventsRepository.class);
                }
        }
}