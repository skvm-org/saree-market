package com.sareemarket.auth;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Spring Security setup: stateless, JWT bearer tokens signed with HS256, BCrypt passwords, no CSRF (no cookies).
 * Open without a token: health, register and login. /api/v1/admin/** needs ROLE_ADMIN. Everything else needs a valid
 * token; finer rules are method-level @PreAuthorize checks on the controllers (@EnableMethodSecurity).
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfig {

	/**
	 * Builds the filter chain. A token is checked for signature and expiry, then the converter below looks the user up in
	 * the database. The role used for authorization is the one inside the token, not the current database role.
	 * UserContextFilter runs after token authentication so the log shows who called.
	 */
	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, UserRepository users) throws Exception {
		return http.csrf(AbstractHttpConfigurer::disable)
			// Uses the CORS mappings from common/CorsConfig (allowed origins from config).
			.cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(a -> a
				.requestMatchers("/api/v1/health", "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
				.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.oauth2ResourceServer(o -> o
				.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter(users)))
				.authenticationEntryPoint(unauthorized())
				.accessDeniedHandler(forbidden()))
			.exceptionHandling(e -> e.authenticationEntryPoint(unauthorized()).accessDeniedHandler(forbidden()))
			.addFilterAfter(new UserContextFilter(), BearerTokenAuthenticationFilter.class)
			.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** Signs tokens with the shared secret (used by JwtService). */
	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
	}

	/** Verifies incoming tokens: HS256 signature and expiry. Issuer and audience are not checked. */
	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties) {
		return NimbusJwtDecoder.withSecretKey(secretKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
	}

	private static SecretKey secretKey(JwtProperties properties) {
		return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	/**
	 * Turns a verified token into an authentication. A token stays valid for its lifetime, so this checks on every request
	 * that the user still exists and is enabled (one database read per request); if not, the request gets 401 UNAUTHORIZED.
	 * Authorities come from the role claim as ROLE_ plus the role name.
	 */
	private static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(UserRepository users) {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("role");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return jwt -> {
			boolean enabled = users.findById(Long.valueOf(jwt.getSubject())).map(AppUser::isEnabled).orElse(false);
			if (!enabled) {
				throw new DisabledException("Account disabled or removed");
			}
			return converter.convert(jwt);
		};
	}

	/** For a missing, invalid or expired token, or a disabled user: 401 UNAUTHORIZED. Security rejects requests before controllers run, so the standard error body is written by hand. */
	private static AuthenticationEntryPoint unauthorized() {
		return (request, response, e) -> writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
				"Login required");
	}

	/** For a logged-in caller without the needed role: 403 FORBIDDEN in the standard error body. */
	private static AccessDeniedHandler forbidden() {
		return (request, response, e) -> writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
				"You do not have access to this");
	}

	/** Writes {code, message, fields, requestId} as JSON; requestId is included only when the request log filter has set one. */
	private static void writeError(jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String code,
			String message) throws java.io.IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String requestId = org.slf4j.MDC.get(com.sareemarket.common.RequestContext.REQUEST_ID);
		response.getWriter()
			.write("{\"code\":\"%s\",\"message\":\"%s\",\"fields\":{}%s}".formatted(code, message,
					requestId == null ? "" : ",\"requestId\":\"" + requestId + "\""));
	}

}
