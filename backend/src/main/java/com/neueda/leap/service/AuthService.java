package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidCredentialsException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.CashTransactions;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AuthenticationResponse;
import com.neueda.leap.model.dto.ForgotPasswordResponse;
import com.neueda.leap.model.dto.RegisterRequest;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.CashTransactionRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    // Registration rules, kept in step with the register page (register.component.ts)
    static final int MAX_NAME_LENGTH = 100;
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_LENGTH = 72;   // BCrypt only uses the first 72 bytes
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,30}");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*[0-9].*");

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final BigDecimal openingDeposit;
    // A hash of a random password, checked against for unknown usernames so they take as long as known ones
    private volatile String unknownUserHash;

    public AuthService(UserRepository userRepository,
                       ClientRepository clientRepository,
                       AccountRepository accountRepository,
                       CashTransactionRepository cashTransactionRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       @Value("${leap.accounts.opening-deposit:100000}") BigDecimal openingDeposit) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.openingDeposit = openingDeposit;
    }

    /**
     * Signs up a new trader: their client record, their sign-in and a trading account opened with a
     * simulated opening deposit (real payments are out of scope for this phase), all in one transaction
     * so a half-created sign-up is never left behind. Usernames and emails are unique regardless of case.
     */
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
        String firstName = request.getFirstName().trim();
        String lastName = request.getLastName().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String username = request.getUsername().trim();
        String password = request.getPassword();
        validateRegistration(firstName, lastName, email, username, password);

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new InvalidInputException("That username is already taken");
        }
        if (clientRepository.existsByEmailIgnoreCase(email)) {
            throw new InvalidInputException("An account with this email already exists");
        }

        Clients savedClient;
        Users savedUser;
        try {
            savedClient = clientRepository.save(new Clients(firstName, lastName, email));
            savedUser = userRepository.save(new Users(savedClient.getClientId(), username, passwordEncoder.encode(password)));
            openAccount(savedClient.getClientId());
        } catch (DataIntegrityViolationException e) {
            // Someone registered the same username or email between the checks above and these inserts
            throw new InvalidInputException("That username or email is already registered");
        }

        String token = jwtUtil.generateToken(savedUser);
        return new AuthenticationResponse(
            token,
            savedUser.getUserId(),
            savedUser.getUsername(),
            savedUser.getClientId(),
            savedClient.getEmail(),
            savedUser.getRole()
        );
    }

    /** The new client's trading account, funded by a recorded deposit so History shows where the cash came from. */
    private void openAccount(Integer clientId) {
        LocalDate today = LocalDate.now();
        Accounts account = accountRepository.save(new Accounts(clientId, today, openingDeposit));
        if (openingDeposit.signum() > 0) {
            cashTransactionRepository.save(new CashTransactions(account.getAccountId(), "DEPOSIT", openingDeposit, today));
        }
    }

    /** Throws InvalidInputException with a message the register page can show as is. */
    static void validateRegistration(String firstName, String lastName, String email, String username, String password) {
        if (firstName.length() > MAX_NAME_LENGTH || lastName.length() > MAX_NAME_LENGTH) {
            throw new InvalidInputException("Names can be at most " + MAX_NAME_LENGTH + " characters");
        }
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new InvalidInputException("Please enter a valid email address");
        }
        if (!USERNAME.matcher(username).matches()) {
            throw new InvalidInputException(
                "Usernames need 3 to 30 characters: letters, numbers, dots, dashes or underscores");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH
            || !HAS_LETTER.matcher(password).matches() || !HAS_DIGIT.matcher(password).matches()) {
            throw new InvalidInputException("Passwords need " + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_LENGTH
                + " characters, including at least one letter and one number");
        }
    }

    public AuthenticationResponse login(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            throw new InvalidInputException("Username and password are required");
        }

        // An unknown username, a wrong password and a disabled user all get the same answer, after the same
        // password check, so sign-in can't be used to find out which usernames exist
        Users user = userRepository.findByUsername(username).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(password, user == null ? unknownUserHash() : user.getPassword());
        if (user == null || !passwordMatches || Boolean.FALSE.equals(user.getIsActive())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String email = findClientEmail(user.getClientId());
        String token = jwtUtil.generateToken(user);

        return new AuthenticationResponse(token, user.getUserId(), user.getUsername(), user.getClientId(), email, user.getRole());
    }

    private String unknownUserHash() {
        if (unknownUserHash == null) {
            unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
        }
        return unknownUserHash;
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