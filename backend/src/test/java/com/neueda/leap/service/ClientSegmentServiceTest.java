package com.neueda.leap.service;

import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.ClientSegmentMetricsProjection;
import com.neueda.leap.model.dto.ClientSegmentQueryRequest;
import com.neueda.leap.model.dto.ClientSegmentResult;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientSegmentServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private UserRepository userRepository;

    private ClientSegmentService clientSegmentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clientSegmentService = new ClientSegmentService(clientRepository, userRepository);
    }

    @Test
    void getClientSegmentsPersistsRecalculatedSegment() {
        Users user = new Users();
        user.setUserId(9);

        Clients client = new Clients(12, "Ada", "Lovelace", "ada@example.com", "Dormant");

        when(userRepository.findById(9)).thenReturn(Optional.of(user));
        when(clientRepository.findClientSegmentMetrics(90)).thenReturn(List.of(
                projection(12, "Ada", "Lovelace", "ada@example.com", new BigDecimal("300000.00"), 15L)
        ));
        when(clientRepository.findAllById(List.of(12))).thenReturn(List.of(client));

        List<ClientSegmentResult> results = clientSegmentService.getClientSegments(9, new ClientSegmentQueryRequest());

        assertEquals(1, results.size());
        assertEquals("Premier", results.get(0).getSegment());
        assertEquals("Premier", client.getSegment());
        verify(clientRepository).saveAll(List.of(client));
    }

    @Test
    void getClientSegmentsSkipsSaveWhenStoredSegmentAlreadyMatches() {
        Users user = new Users();
        user.setUserId(9);

        Clients client = new Clients(12, "Ada", "Lovelace", "ada@example.com", "Premier");

        when(userRepository.findById(9)).thenReturn(Optional.of(user));
        when(clientRepository.findClientSegmentMetrics(90)).thenReturn(List.of(
                projection(12, "Ada", "Lovelace", "ada@example.com", new BigDecimal("300000.00"), 15L)
        ));
        when(clientRepository.findAllById(List.of(12))).thenReturn(List.of(client));

        List<ClientSegmentResult> results = clientSegmentService.getClientSegments(9, new ClientSegmentQueryRequest());

        assertEquals(1, results.size());
        assertEquals("Premier", results.get(0).getSegment());
        verify(clientRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    private ClientSegmentMetricsProjection projection(
            Integer clientId,
            String firstName,
            String lastName,
            String email,
            BigDecimal totalPortfolioValue,
            Long recentFilledOrderCount
    ) {
        return new ClientSegmentMetricsProjection() {
            @Override
            public Integer getClientId() {
                return clientId;
            }

            @Override
            public String getFirstName() {
                return firstName;
            }

            @Override
            public String getLastName() {
                return lastName;
            }

            @Override
            public String getEmail() {
                return email;
            }

            @Override
            public BigDecimal getTotalPortfolioValue() {
                return totalPortfolioValue;
            }

            @Override
            public Long getRecentFilledOrderCount() {
                return recentFilledOrderCount;
            }
        };
    }
}