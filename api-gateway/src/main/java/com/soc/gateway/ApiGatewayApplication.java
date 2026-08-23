package com.soc.gateway;

import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.util.List;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @Bean
    WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    CorsWebFilter corsFilter(
            @Value("${app.cors-origin}") String allowedOrigin
    ) {
        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(allowedOrigin)
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Content-Type",
                        "Authorization",
                        "X-API-KEY"
                )
        );

        configuration.setExposedHeaders(
                List.of("Content-Disposition")
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return new CorsWebFilter(source);
    }

    @Bean
GlobalFilter authenticationFilter(
        WebClient webClient,
        @Value("${app.security-url}") String securityUrl
) {
    class OrderedAuthenticationFilter
            implements GlobalFilter, Ordered {

        @Override
        public Mono<Void> filter(
                ServerWebExchange exchange,
                GatewayFilterChain chain
        ) {
            String path =
                    exchange.getRequest().getPath().value();

            HttpMethod method =
                    exchange.getRequest().getMethod();

            if (method == HttpMethod.OPTIONS ||
                    path.startsWith("/actuator")) {
                return chain.filter(exchange);
            }

            if (isPublicAuthenticationEndpoint(path, method)) {
                return chain.filter(exchange);
            }

            if (path.equals("/api/customers") &&
                    method == HttpMethod.POST) {
                return chain.filter(exchange);
            }

            String authorization =
                    exchange.getRequest()
                            .getHeaders()
                            .getFirst("Authorization");

            String apiKey =
                    exchange.getRequest()
                            .getHeaders()
                            .getFirst("X-API-KEY");

            if (authorization != null &&
                    authorization.startsWith("Bearer ")) {
                return validateSession(
                        webClient,
                        securityUrl,
                        authorization
                ).flatMap(validation ->
                        processValidation(
                                exchange,
                                chain,
                                validation
                        )
                ).onErrorResume(error ->
                        unauthorized(
                                exchange,
                                "Authentication service unavailable"
                        )
                );
            }

            if (apiKey != null && !apiKey.isBlank()) {
                return validateApiKey(
                        webClient,
                        securityUrl,
                        apiKey
                ).flatMap(validation ->
                        processValidation(
                                exchange,
                                chain,
                                validation
                        )
                ).onErrorResume(error ->
                        unauthorized(
                                exchange,
                                "Authentication service unavailable"
                        )
                );
            }

            return unauthorized(
                    exchange,
                    "Login is required"
            );
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }

    return new OrderedAuthenticationFilter();
}

    private static boolean isPublicAuthenticationEndpoint(
            String path,
            HttpMethod method
    ) {
        if (method != HttpMethod.POST) {
            return false;
        }

        return path.equals("/api/auth/login") ||
                path.equals("/api/auth/register");
    }

    private static Mono<Validation> validateSession(
            WebClient webClient,
            String securityUrl,
            String authorization
    ) {
        return webClient
                .post()
                .uri(securityUrl + "/auth/validate")
                .header("Authorization", authorization)
                .retrieve()
                .bodyToMono(Validation.class);
    }

    private static Mono<Validation> validateApiKey(
            WebClient webClient,
            String securityUrl,
            String apiKey
    ) {
        return webClient
                .post()
                .uri(securityUrl + "/security/validate")
                .header("X-API-KEY", apiKey)
                .retrieve()
                .bodyToMono(Validation.class);
    }

    private static Mono<Void> processValidation(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            Validation validation
    ) {
        if (validation == null || !validation.valid()) {
            return unauthorized(
                    exchange,
                    "Invalid or expired login"
            );
        }

        String path =
                exchange.getRequest().getPath().value();

        HttpMethod method =
                exchange.getRequest().getMethod();

        if (isAdminOnly(path, method) &&
                !"ADMIN".equals(validation.role())) {
            return forbidden(
                    exchange,
                    "Administrator access is required"
            );
        }

        /*
         * Remove any identity headers supplied by the browser.
         * Add only the values validated by the gateway.
         */
        ServerWebExchange securedExchange =
                exchange.mutate()
                        .request(request ->
                                request.headers(headers -> {
                                    headers.remove("X-CLIENT-ROLE");
                                    headers.remove("X-CLIENT-USERNAME");
                                    headers.remove("X-CUSTOMER-ID");

                                    headers.add(
                                            "X-CLIENT-ROLE",
                                            valueOrEmpty(
                                                    validation.role()
                                            )
                                    );

                                    headers.add(
                                            "X-CLIENT-USERNAME",
                                            valueOrEmpty(
                                                    validation.username()
                                            )
                                    );

                                    headers.add(
                                            "X-CUSTOMER-ID",
                                            valueOrEmpty(
                                                    validation.customerId()
                                            )
                                    );
                                })
                        )
                        .build();

        return chain.filter(securedExchange);
    }

    private static boolean isAdminOnly(
            String path,
            HttpMethod method
    ) {
        /*
         * API-key management is administrator-only.
         */
        if (path.startsWith("/api/security/")) {
            return true;
        }

        /*
         * Customers can view products.
         * Creating, updating and deleting products is admin-only.
         */
        if (path.startsWith("/api/products") &&
                method != HttpMethod.GET) {
            return true;
        }

        /*
         * Viewing the complete order list is admin-only.
         * Customers can still POST an order during checkout.
         */
        if (path.equals("/api/orders") &&
                method == HttpMethod.GET) {
            return true;
        }

        /*
         * Changing order status is admin-only.
         */
        return path.startsWith("/api/orders/") &&
                (method == HttpMethod.PUT ||
                        method == HttpMethod.PATCH ||
                        method == HttpMethod.DELETE);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Mono<Void> unauthorized(
            ServerWebExchange exchange,
            String message
    ) {
        return jsonError(
                exchange,
                HttpStatus.UNAUTHORIZED,
                message
        );
    }

    private static Mono<Void> forbidden(
            ServerWebExchange exchange,
            String message
    ) {
        return jsonError(
                exchange,
                HttpStatus.FORBIDDEN,
                message
        );
    }

    private static Mono<Void> jsonError(
            ServerWebExchange exchange,
            HttpStatus status,
            String message
    ) {
        exchange.getResponse().setStatusCode(status);

        exchange.getResponse()
                .getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        String safeMessage =
                message.replace("\"", "\\\"");

        byte[] responseBody =
                ("{\"error\":\"" + safeMessage + "\"}")
                        .getBytes(StandardCharsets.UTF_8);

        return exchange.getResponse().writeWith(
                Mono.just(
                        exchange.getResponse()
                                .bufferFactory()
                                .wrap(responseBody)
                )
        );
    }

    /*
     * username and customerId will be null when authentication
     * uses an API key instead of a login session.
     */
    record Validation(
            boolean valid,
            String role,
            String username,
            String customerId
    ) {
    }
}