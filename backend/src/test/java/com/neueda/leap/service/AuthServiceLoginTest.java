package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidCredentialsException;
import com.neueda.leap.model.Users;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.CashTransactionRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** BR-01, signing in securely: a failed sign-in never says whether the username exists. */
class AuthServiceLoginTest {
    private UserRepository userRepository;
    private AuthService authService;
    private Users sam;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        // A real (but quick) BCrypt, so unknown usernames really are checked against a hash
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.generateToken(any())).thenReturn("jwt");
        authService = new AuthService(userRepository, mock(ClientRepository.class), mock(AccountRepository.class),
                mock(CashTransactionRepository.class), encoder, jwtUtil, new BigDecimal("100000"));

        sam = new Users(null, "sam", encoder.encode("Test123"));
        sam.setUserId(4);
        when(userRepository.findByUsername("sam")).thenReturn(Optional.of(sam));
    }

    @Test
    void rightPasswordSignsIn() {
        assertEquals("jwt", authService.login("sam", "Test123").getToken());
    }

    @Test
    void unknownUsernameAndWrongPasswordGetTheSameAnswer() {
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
                () -> authService.login("nobody", "Test123"));
        InvalidCredentialsException wrong = assertThrows(InvalidCredentialsException.class,
                () -> authService.login("sam", "wrong"));

        assertEquals("Invalid username or password", unknown.getMessage());
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }

    @Test
    void disabledUserCannotSignInEvenWithTheRightPassword() {
        sam.setIsActive(false);

        InvalidCredentialsException e = assertThrows(InvalidCredentialsException.class,
                () -> authService.login("sam", "Test123"));
        assertEquals("Invalid username or password", e.getMessage());
    }
}
