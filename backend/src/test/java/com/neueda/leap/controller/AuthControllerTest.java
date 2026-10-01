package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.exception.InvalidCredentialsException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.AuthenticationResponse;
import com.neueda.leap.model.dto.LoginRequest;
import com.neueda.leap.model.dto.RegisterRequest;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AuthControllerTest.TestConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc; //Creates a mock MVC to simulate HTTP requests and test the controller endpoints

    @Autowired
    private ObjectMapper objectMapper; //Turns Java objects into JSON and vice versa

    @Autowired
    private FakeAuthService authService; //A fake implementation of AuthService to simulate service behavior in tests
//Side note: A fake auth service is used here instead of the real one to isolate controller tests from service logic
    @BeforeEach // Resets the fake AuthService before each test to ensure a clean state
    void setUp() {
        authService.reset();
    }

    @Test
    void registerReturnsCreatedAuthenticationResponse() throws Exception { // Tests that the /auth/register endpoint returns the expected AuthenticationResponse
        //Fills out an authentication response to be returned by the fake AuthService
        authService.registerResponse = new AuthenticationResponse(
                "jwt-register-token",
                101,
                "newuser",
                42,
            "newuser@example.com",
            "USER"
        );
        //Performs a POST request to the /auth/register endpoint with the registration details and verifies the response
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName": "Doe",
                                  "email": "newuser@example.com",
                                  "username": "newuser",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-register-token"))
                .andExpect(jsonPath("$.userId").value(101))
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.clientId").value(42))
                .andExpect(jsonPath("$.email").value("newuser@example.com"));

        //Verifies that the captured registration request in the fake AuthService matches the input data
            assertEquals("Jane", authService.capturedRegisterRequest.getFirstName());
            assertEquals("Doe", authService.capturedRegisterRequest.getLastName());
            assertEquals("newuser@example.com", authService.capturedRegisterRequest.getEmail());
            assertEquals("newuser", authService.capturedRegisterRequest.getUsername());
            assertEquals("Password123!", authService.capturedRegisterRequest.getPassword());
    }

    @Test
    void loginReturnsAuthenticationResponse() throws Exception { // Tests that the /auth/login endpoint returns the expected AuthenticationResponse
        authService.loginResponse = new AuthenticationResponse(
                "jwt-login-token",
                202,
                "existinguser",
                77,
            "existinguser@example.com",
            "USER"
        );

        //Performs a POST request to the /auth/login endpoint with the login details and verifies the response
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest("existinguser", "SecretPass1!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-login-token"))
                .andExpect(jsonPath("$.userId").value(202))
                .andExpect(jsonPath("$.username").value("existinguser"))
                .andExpect(jsonPath("$.clientId").value(77))
                .andExpect(jsonPath("$.email").value("existinguser@example.com"));
        //Verifies that the captured login details in the fake AuthService match the input data
        assertEquals("existinguser", authService.capturedUsername);
        assertEquals("SecretPass1!", authService.capturedPassword);
    }

    @Test
    // Tests that the /auth/register endpoint returns a Bad Request response when required fields are missing
    void registerReturnsBadRequestWhenRequiredFieldsMissing() throws Exception {
        authService.registerException = new InvalidInputException("All registration fields are required");
        //Sets up the fake AuthService to throw an InvalidInputException when registration is attempted with missing fields
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName": "Doe",
                                  "email": "",
                                  "username": "newuser",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("All registration fields are required"))
                .andExpect(jsonPath("$.error").value("InvalidInputException"));
    }

    @Test
    // Tests that the /auth/register endpoint returns a Bad Request response when the username already exists
    void registerReturnsBadRequestWhenUsernameAlreadyExists() throws Exception {
        authService.registerException = new InvalidInputException("Username already exists");
        //Sets up the fake AuthService to throw an InvalidInputException when registration is attempted with an existing username
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName": "Doe",
                                  "email": "newuser@example.com",
                                  "username": "newuser",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Username already exists"))
                .andExpect(jsonPath("$.error").value("InvalidInputException"));
    }

    @Test
    // Tests that the /auth/login endpoint returns a Bad Request response when login credentials are missing
    void loginReturnsBadRequestWhenCredentialsMissing() throws Exception {
        authService.loginException = new InvalidInputException("Username and password are required");
        //Performs a POST request to the /auth/login endpoint with missing credentials and verifies the response
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest("existinguser", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Username and password are required"))
                .andExpect(jsonPath("$.error").value("InvalidInputException"));
    }

    @Test
    // Tests that the /auth/login endpoint returns a Not Found response when the user does not exist
    void loginReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        authService.loginException = new UserNotFoundException("User not found");
        //Performs a POST request to the /auth/login endpoint with a non-existent user and verifies the response
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest("missinguser", "SecretPass1!"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.error").value("UserNotFoundException"));
    }

    @Test
    // Tests that the /auth/login endpoint returns an Unauthorized response when the password is incorrect
    void loginReturnsUnauthorizedWhenPasswordIsWrong() throws Exception {
        authService.loginException = new InvalidCredentialsException("Invalid username or password");
        //Performs a POST request to the /auth/login endpoint with an incorrect password and verifies the response
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest("existinguser", "WrongPass1!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.error").value("InvalidCredentialsException"));
    }

    @Test
    // Tests that the /auth/login endpoint returns a Bad Request response when the request body is empty JSON
    void loginReturnsBadRequestWhenRequestBodyIsEmptyJson() throws Exception {
        authService.loginException = new InvalidInputException("Username and password are required");
        //Performs a POST request to the /auth/login endpoint with an empty JSON body and verifies the response
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Username and password are required"))
                .andExpect(jsonPath("$.error").value("InvalidInputException"));
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        FakeAuthService authService() {
            return new FakeAuthService();
        }

        @Bean
        JwtUtil jwtUtil() {
            return new JwtUtil();
        }
    }

    static class FakeAuthService extends AuthService {
        private RegisterRequest capturedRegisterRequest;
        private String capturedUsername;
        private String capturedPassword;
        private AuthenticationResponse registerResponse;
        private AuthenticationResponse loginResponse;
        private RuntimeException registerException;
        private RuntimeException loginException;

        private FakeAuthService() {
            super(null, null, null, null);
        }

        private void reset() {
            capturedRegisterRequest = null;
            capturedUsername = null;
            capturedPassword = null;
            registerResponse = null;
            loginResponse = null;
            registerException = null;
            loginException = null;
        }

        @Override
        public AuthenticationResponse register(RegisterRequest request) {
            this.capturedRegisterRequest = request;
            if (registerException != null) {
                throw registerException;
            }
            return registerResponse;
        }

        @Override
        public AuthenticationResponse login(String username, String password) {
            this.capturedUsername = username;
            this.capturedPassword = password;
            if (loginException != null) {
                throw loginException;
            }
            return loginResponse;
        }
    }
}