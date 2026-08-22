package com.soc.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@SpringBootApplication
public class SecurityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityServiceApplication.class, args);
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * Creates the initial API key and administrator account.
     *
     * The administrator password is stored as a BCrypt hash.
     * The API key is stored as a SHA-256 hash.
     */
    @Bean
    CommandLineRunner seedData(
            ApiClientRepository apiClients,
            UserAccountRepository accounts,
            BCryptPasswordEncoder passwordEncoder,
            @Value("${app.initial-admin-key}") String initialAdminKey,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword
    ) {
        return args -> {
            if (apiClients.count() == 0) {
                apiClients.save(new ApiClient(
                        null,
                        "Initial Admin",
                        sha256(initialAdminKey),
                        "ADMIN",
                        true,
                        Instant.now()
                ));
            }

            String normalizedUsername = normalizeUsername(adminUsername);

            if (accounts.findByUsername(normalizedUsername).isEmpty()) {
                accounts.save(new UserAccount(
                        null,
                        normalizedUsername,
                        passwordEncoder.encode(adminPassword),
                        "ADMIN",
                        null,
                        true,
                        Instant.now()
                ));
            }
        };
    }

    static String sha256(String value) {
        try {
            byte[] result = MessageDigest
                    .getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash value", exception);
        }
    }

    static String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }

        return username.trim().toLowerCase(Locale.ROOT);
    }
}

/* =========================================================
   API KEY DOCUMENT
   ========================================================= */

@Document("api_clients")
record ApiClient(
        @Id String id,
        String name,
        String keyHash,
        String role,
        boolean active,
        Instant createdAt
) {
}

interface ApiClientRepository extends MongoRepository<ApiClient, String> {

    Optional<ApiClient> findByKeyHashAndActiveTrue(String keyHash);
}

/* =========================================================
   USER ACCOUNT DOCUMENT
   ========================================================= */

@Document("user_accounts")
record UserAccount(
        @Id String id,
        String username,
        String passwordHash,
        String role,
        String customerId,
        boolean active,
        Instant createdAt
) {
}

interface UserAccountRepository extends MongoRepository<UserAccount, String> {

    Optional<UserAccount> findByUsername(String username);
}

/* =========================================================
   LOGIN SESSION DOCUMENT
   ========================================================= */

@Document("login_sessions")
record LoginSession(
        @Id String id,
        String tokenHash,
        String username,
        String role,
        String customerId,
        Instant createdAt,
        Instant expiresAt
) {
}

interface LoginSessionRepository extends MongoRepository<LoginSession, String> {

    Optional<LoginSession> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);
}

/* =========================================================
   REQUEST AND RESPONSE OBJECTS
   ========================================================= */

record RegisterRequest(
        String username,
        String password,
        String customerId
) {
}

record LoginRequest(
        String username,
        String password
) {
}

record LoginResponse(
        String token,
        String username,
        String role,
        String customerId,
        Instant expiresAt
) {
}

record AuthValidation(
        boolean valid,
        String role,
        String username,
        String customerId
) {
}

record AccountResponse(
        String id,
        String username,
        String role,
        String customerId,
        boolean active,
        Instant createdAt
) {
}

record CreateKeyRequest(
        String name,
        String role
) {
}

record KeyCreated(
        String id,
        String apiKey,
        String role
) {
}

record ApiKeyValidation(
        boolean valid,
        String role
) {
}

/* =========================================================
   LOGIN AND REGISTRATION CONTROLLER
   ========================================================= */

@RestController
@RequestMapping("/auth")
class AuthenticationController {

    private final UserAccountRepository accounts;
    private final LoginSessionRepository sessions;
    private final BCryptPasswordEncoder passwordEncoder;
    private final long sessionDurationHours;

    AuthenticationController(
            UserAccountRepository accounts,
            LoginSessionRepository sessions,
            BCryptPasswordEncoder passwordEncoder,
            @Value("${app.session.duration-hours:8}")
            long sessionDurationHours
    ) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.sessionDurationHours = sessionDurationHours;
    }

    /*
     * Customer registration.
     *
     * Admin accounts cannot be created through this endpoint.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse register(@RequestBody RegisterRequest request) {
        String username =
                SecurityServiceApplication.normalizeUsername(request.username());

        if (username.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email or username is required"
            );
        }

        if (request.password() == null || request.password().length() < 8) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must contain at least 8 characters"
            );
        }

        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer ID is required"
            );
        }

        if (accounts.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account already exists for this email"
            );
        }

        UserAccount account = accounts.save(new UserAccount(
                null,
                username,
                passwordEncoder.encode(request.password()),
                "CUSTOMER",
                request.customerId().trim(),
                true,
                Instant.now()
        ));

        return toResponse(account);
    }

    @PostMapping("/login")
    LoginResponse login(@RequestBody LoginRequest request) {
        String username =
                SecurityServiceApplication.normalizeUsername(request.username());

        UserAccount account = accounts.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username or password"
                ));

        if (!account.active()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This account is disabled"
            );
        }

        if (request.password() == null ||
                !passwordEncoder.matches(
                        request.password(),
                        account.passwordHash()
                )) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid username or password"
            );
        }

        String rawToken =
                "soc_session_" +
                UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "");

        Instant now = Instant.now();
        Instant expiresAt =
                now.plus(sessionDurationHours, ChronoUnit.HOURS);

        sessions.save(new LoginSession(
                null,
                SecurityServiceApplication.sha256(rawToken),
                account.username(),
                account.role(),
                account.customerId(),
                now,
                expiresAt
        ));

        return new LoginResponse(
                rawToken,
                account.username(),
                account.role(),
                account.customerId(),
                expiresAt
        );
    }

    @PostMapping("/validate")
    AuthValidation validate(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization
    ) {
        String token = bearerToken(authorization);

        if (token == null) {
            return invalidValidation();
        }

        Optional<LoginSession> optionalSession =
                sessions.findByTokenHash(
                        SecurityServiceApplication.sha256(token)
                );

        if (optionalSession.isEmpty()) {
            return invalidValidation();
        }

        LoginSession session = optionalSession.get();

        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.delete(session);
            return invalidValidation();
        }

        Optional<UserAccount> optionalAccount =
                accounts.findByUsername(session.username());

        if (optionalAccount.isEmpty() ||
                !optionalAccount.get().active()) {
            sessions.delete(session);
            return invalidValidation();
        }

        return new AuthValidation(
                true,
                session.role(),
                session.username(),
                session.customerId()
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization
    ) {
        String token = bearerToken(authorization);

        if (token != null) {
            sessions.deleteByTokenHash(
                    SecurityServiceApplication.sha256(token)
            );
        }
    }

    private static String bearerToken(String authorization) {
        if (authorization == null ||
                !authorization.startsWith("Bearer ")) {
            return null;
        }

        String token = authorization.substring(7).trim();

        return token.isBlank() ? null : token;
    }

    private static AuthValidation invalidValidation() {
        return new AuthValidation(
                false,
                null,
                null,
                null
        );
    }

    private static AccountResponse toResponse(UserAccount account) {
        return new AccountResponse(
                account.id(),
                account.username(),
                account.role(),
                account.customerId(),
                account.active(),
                account.createdAt()
        );
    }
}

/* =========================================================
   API KEY CONTROLLER
   ========================================================= */

@RestController
@RequestMapping("/security")
class SecurityController {

    private final ApiClientRepository repository;

    SecurityController(ApiClientRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/validate")
    ApiKeyValidation validateApiKey(
            @RequestHeader(
                    value = "X-API-KEY",
                    required = false
            ) String apiKey
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return new ApiKeyValidation(false, null);
        }

        return repository
                .findByKeyHashAndActiveTrue(
                        SecurityServiceApplication.sha256(apiKey)
                )
                .map(client -> new ApiKeyValidation(
                        true,
                        client.role()
                ))
                .orElse(new ApiKeyValidation(false, null));
    }

    @GetMapping("/clients")
    List<Map<String, Object>> clients() {
        return repository.findAll()
                .stream()
                .map(client -> {
                    Map<String, Object> result =
                            new LinkedHashMap<>();

                    result.put("id", client.id());
                    result.put("name", client.name());
                    result.put("role", client.role());
                    result.put("active", client.active());
                    result.put("createdAt", client.createdAt());

                    return result;
                })
                .toList();
    }

    @PostMapping("/keys")
    @ResponseStatus(HttpStatus.CREATED)
    KeyCreated createApiKey(
            @RequestBody CreateKeyRequest request
    ) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client name is required"
            );
        }

        String rawKey =
                "soc_" +
                UUID.randomUUID().toString().replace("-", "");

        String role = request.role() == null
                ? "CUSTOMER"
                : request.role().trim().toUpperCase(Locale.ROOT);

        if (!role.equals("CUSTOMER") &&
                !role.equals("ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Role must be CUSTOMER or ADMIN"
            );
        }

        ApiClient client = repository.save(new ApiClient(
                null,
                request.name().trim(),
                SecurityServiceApplication.sha256(rawKey),
                role,
                true,
                Instant.now()
        ));

        return new KeyCreated(
                client.id(),
                rawKey,
                client.role()
        );
    }

    @DeleteMapping("/clients/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeApiKey(@PathVariable String id) {
        ApiClient client = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "API client was not found"
                ));

        repository.save(new ApiClient(
                client.id(),
                client.name(),
                client.keyHash(),
                client.role(),
                false,
                client.createdAt()
        ));
    }
}