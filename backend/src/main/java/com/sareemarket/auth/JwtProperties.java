package com.sareemarket.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Settings under app.jwt. HS256 needs a secret of at least 32 bytes, so a short one fails at startup.
 * The secret comes from the JWT_SECRET environment variable (a fixed dev default exists in the local profile only).
 *
 * @param secret key used to both sign and verify tokens; anyone who has it can forge a token for any user
 * @param expiry how long a token is valid after login, 24 hours by default; there is no refresh or revocation
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(@NotBlank @Size(min = 32) String secret, @DefaultValue("24h") Duration expiry) {
}
