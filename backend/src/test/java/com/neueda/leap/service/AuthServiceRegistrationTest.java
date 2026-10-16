package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.CashTransactions;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.RegisterRequest;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.CashTransactionRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceRegistrationTest {
    private UserRepository userRepository;
    private ClientRepository clientRepository;
    private AccountRepository accountRepository;
    private CashTransactionRepository cashTransactionRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        clientRepository = mock(ClientRepository.class);
        accountRepository = mock(AccountRepository.class);
        cashTransactionRepository = mock(CashTransactionRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hashed");
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.generateToken(any())).thenReturn("jwt");
        authService = new AuthService(userRepository, clientRepository, accountRepository, cashTransactionRepository,
                encoder, jwtUtil, new BigDecimal("100000"));

        when(clientRepository.save(any())).thenAnswer(inv -> {
            Clients c = inv.getArgument(0);
            c.setClientId(7);
            return c;
        });
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.save(any())).thenAnswer(inv -> {
            Accounts a = inv.getArgument(0);
            a.setAccountId(70);
            return a;
        });
    }

    private static RegisterRequest request(String email, String username, String password) {
        RegisterRequest r = new RegisterRequest();
        r.setFirstName("  Jane ");
        r.setLastName("Doe");
        r.setEmail(email);
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }

    @Test
    void opensAFundedAccountWithTheSignUp() {
        authService.register(request(" Jane@Example.COM ", " jane ", "Secret123"));

        ArgumentCaptor<Clients> client = ArgumentCaptor.forClass(Clients.class);
        verify(clientRepository).save(client.capture());
        assertEquals("Jane", client.getValue().getFirstName());
        assertEquals("jane@example.com", client.getValue().getEmail());
        ArgumentCaptor<Users> user = ArgumentCaptor.forClass(Users.class);
        verify(userRepository).save(user.capture());
        assertEquals("jane", user.getValue().getUsername());
        assertEquals("TRADER", user.getValue().getRole());

        ArgumentCaptor<Accounts> account = ArgumentCaptor.forClass(Accounts.class);
        verify(accountRepository).save(account.capture());
        assertEquals(7, account.getValue().getClientId());
        assertEquals(0, new BigDecimal("100000").compareTo(account.getValue().getBalance()));
        ArgumentCaptor<CashTransactions> deposit = ArgumentCaptor.forClass(CashTransactions.class);
        verify(cashTransactionRepository).save(deposit.capture());
        assertEquals("DEPOSIT", deposit.getValue().getTxnType());
        assertEquals(70, deposit.getValue().getAccountId());
    }

    @Test
    void takenUsernameIsCheckedWithoutCase() {
        when(userRepository.existsByUsernameIgnoreCase("Jane")).thenReturn(true);

        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> authService.register(request("jane@example.com", "Jane", "Secret123")));
        assertEquals("That username is already taken", e.getMessage());
        verify(clientRepository, never()).save(any());
    }

    @Test
    void takenEmailIsTurnedDown() {
        when(clientRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(true);

        assertThrows(InvalidInputException.class,
                () -> authService.register(request("JANE@example.com", "jane", "Secret123")));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void aSignUpRacingAnotherIsTurnedDownNotA500() {
        when(userRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> authService.register(request("jane@example.com", "jane", "Secret123")));
        assertEquals("That username or email is already registered", e.getMessage());
    }

    @Test
    void noDepositIsRecordedWhenAccountsOpenEmpty() {
        AuthService noFunding = new AuthService(userRepository, clientRepository, accountRepository,
                cashTransactionRepository, mock(PasswordEncoder.class), mock(JwtUtil.class), BigDecimal.ZERO);

        noFunding.register(request("jane@example.com", "jane", "Secret123"));

        verify(accountRepository).save(any());
        verify(cashTransactionRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({
            "jane@example.com, ab,          Secret123",   // username too short
            "jane@example.com, jane doe,    Secret123",   // space in username
            "jane@example.com, jane,        Secret1",     // password too short
            "jane@example.com, jane,        secretpass",  // no number
            "jane@example.com, jane,        12345678",    // no letter
            "not-an-email,     jane,        Secret123",
            "jane@example,     jane,        Secret123"
    })
    void turnsDownInputThatBreaksTheRules(String email, String username, String password) {
        assertThrows(InvalidInputException.class,
                () -> AuthService.validateRegistration("Jane", "Doe", email, username, password));
    }

    @Test
    void acceptsATypicalSignUp() {
        assertDoesNotThrow(() -> AuthService.validateRegistration("Jane", "Doe", "jane.doe@example.co.uk", "jane.doe-99", "Secret123"));
    }
}
