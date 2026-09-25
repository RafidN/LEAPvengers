package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidCredentialsException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AuthenticationResponse;
import com.neueda.leap.model.dto.ForgotPasswordResponse;
import com.neueda.leap.model.dto.RegisterRequest;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       ClientRepository clientRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        if (request == null
            || isBlank(request.getFirstName())
            || isBlank(request.getLastName())
            || isBlank(request.getEmail())
            || isBlank(request.getUsername())
            || isBlank(request.getPassword())) {
            throw new InvalidInputException("All registration fields are required");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new InvalidInputException("Username already exists");
        }

        Clients client = new Clients(request.getFirstName(), request.getLastName(), request.getEmail());
        Clients savedClient = clientRepository.save(client);

        Users user = new Users(savedClient.getClientId(), request.getUsername(), passwordEncoder.encode(request.getPassword()));
        Users savedUser = userRepository.save(user);

        String token = jwtUtil.generateToken(savedUser);
        return new AuthenticationResponse(
            token,
            savedUser.getUserId(),
            savedUser.getUsername(),
            savedUser.getClientId(),
            savedClient.getEmail()
        );
    }

    public AuthenticationResponse login(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            throw new InvalidInputException("Username and password are required");
        }

        Users user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String email = findClientEmail(user.getClientId());
        String token = jwtUtil.generateToken(user);

        return new AuthenticationResponse(token, user.getUserId(), user.getUsername(), user.getClientId(), email);
    }

    public ForgotPasswordResponse forgotPassword(String username, String email) {
        if (isBlank(username) || isBlank(email)) {
            throw new InvalidInputException("Username and email are required");
        }

        Users user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found"));

        String userEmail = findClientEmail(user.getClientId());
        if (userEmail == null || !userEmail.equalsIgnoreCase(email)) {
            throw new InvalidInputException("Provided email does not match account records");
        }

        return new ForgotPasswordResponse(
            "success",
            "Password reset workflow not implemented yet. Please contact support."
        );
    }

    /**
     * Looks up a client's email, tolerating a null client id.
     * Internal users (OPS/ANALYST) have no client_id, and findById(null) would throw.
     */
    private String findClientEmail(Integer clientId) {
        if (clientId == null) {
            return null;
        }
        return clientRepository.findById(clientId)
            .map(Clients::getEmail)
            .orElse(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}