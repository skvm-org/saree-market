package com.sareemarket.auth;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Signs the access tokens. Verification is done by Spring Security's JwtDecoder (see SecurityConfig), not here. */
@Service
class JwtService {

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	JwtService(JwtEncoder encoder, JwtProperties properties) {
		this.encoder = encoder;
		this.properties = properties;
	}

	/**
	 * Builds an HS256 token whose subject is the user id and whose role claim is the user's role at this moment.
	 * It expires after app.jwt.expiry. Nothing else (no phone, no name) is put in the token.
	 */
	String issue(AppUser user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(String.valueOf(user.getId()))
			.claim("role", user.getRole().name())
			.issuedAt(now)
			.expiresAt(now.plus(properties.expiry()))
			.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

}
