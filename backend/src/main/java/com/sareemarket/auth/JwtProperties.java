package com.sareemarket.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HS256 needs a secret of at least 32 bytes, so a short one fails at startup. */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(@NotBlank @Size(min = 32) String secret, @DefaultValue("24h") Duration expiry) {
}
