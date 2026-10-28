package com.sareemarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sareemarket.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtProperties jwtProperties;

	ResultActions postJson(String path, String json) throws Exception {
		return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	String registerCustomer(String phone) throws Exception {
		String json = postJson("/api/v1/auth/register", """
				{"phone":"%s","name":"Priya","password":"password1","role":"CUSTOMER"}""".formatted(phone))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(json, "$.token");
	}

	String registerSeller(String phone) throws Exception {
		String json = postJson("/api/v1/auth/register", """
				{"phone":"%s","name":"Lakshmi","password":"password1","role":"SELLER",
				 "shopName":"Lakshmi Silks","marketLocation":"Dindigul","contactPhone":"%s"}"""
			.formatted(phone, phone)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.token");
	}

	ResultActions getWith(String path, String token) throws Exception {
		return mvc.perform(get(path).header("Authorization", "Bearer " + token));
	}

	@Test
	void registeringASellerCreatesTheShopProfileAndReturnsAUsableToken() throws Exception {
		String token = registerSeller("9000000001");

		assertThat(jdbc.queryForObject("""
				SELECT shop_name FROM seller_profile sp JOIN app_user u ON u.id = sp.user_id
				WHERE u.phone = '9000000001'""", String.class)).isEqualTo("Lakshmi Silks");
		getWith("/api/v1/auth/me", token).andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("SELLER"))
			.andExpect(jsonPath("$.phone").value("9000000001"));
	}

	@Test
	void passwordIsStoredHashedNotPlain() throws Exception {
		registerCustomer("9000000002");

		assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE phone = '9000000002'", String.class))
			.startsWith("$2").doesNotContain("password1");
	}

	@Test
	void registeringTheSamePhoneTwiceIsConflict() throws Exception {
		registerCustomer("9000000003");

		postJson("/api/v1/auth/register", """
				{"phone":"9000000003","name":"Other","password":"password1","role":"CUSTOMER"}""")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PHONE_TAKEN"));
	}

	@Test
	void invalidRegistrationReturnsFieldErrors() throws Exception {
		postJson("/api/v1/auth/register", """
				{"phone":"123","name":"","password":"short","role":"CUSTOMER"}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fields.phone").exists())
			.andExpect(jsonPath("$.fields.name").exists())
			.andExpect(jsonPath("$.fields.password").exists());
	}

	@Test
	void sellerRegistrationWithoutShopDetailsIsRejected() throws Exception {
		postJson("/api/v1/auth/register", """
				{"phone":"9000000004","name":"Lakshmi","password":"password1","role":"SELLER"}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.shopName").exists());
	}

	@Test
	void adminCannotBeCreatedThroughRegistration() throws Exception {
		postJson("/api/v1/auth/register", """
				{"phone":"9000000005","name":"Eve","password":"password1","role":"ADMIN"}""")
			.andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE phone = '9000000005'", Integer.class))
			.isZero();
	}

	@Test
	void loginWorksAndWrongPasswordIsUnauthorized() throws Exception {
		registerCustomer("9000000006");

		postJson("/api/v1/auth/login", """
				{"phone":"9000000006","password":"password1"}""").andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isNotEmpty())
			.andExpect(jsonPath("$.user.role").value("CUSTOMER"));
		postJson("/api/v1/auth/login", """
				{"phone":"9000000006","password":"nope-nope"}""").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void seededAdminCanLogIn() throws Exception {
		postJson("/api/v1/auth/login", """
				{"phone":"9999999999","password":"admin123"}""").andExpect(status().isOk())
			.andExpect(jsonPath("$.user.role").value("ADMIN"));
	}

	@Test
	void disabledUserIsRefusedAtLoginAndOnExistingToken() throws Exception {
		String token = registerCustomer("9000000007");
		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE phone = '9000000007'");

		postJson("/api/v1/auth/login", """
				{"phone":"9000000007","password":"password1"}""").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
		getWith("/api/v1/auth/me", token).andExpect(status().isUnauthorized());
	}

	@Test
	void protectedEndpointsRejectMissingMalformedAndExpiredTokens() throws Exception {
		mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		getWith("/api/v1/auth/me", "not-a-jwt").andExpect(status().isUnauthorized());
		getWith("/api/v1/auth/me", tokenExpiredAnHourAgo()).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() throws Exception {
		String forged = token("some-other-secret-that-is-also-32-chars-long!!", Instant.now().plusSeconds(3600));

		getWith("/api/v1/auth/me", forged).andExpect(status().isUnauthorized());
	}

	@Test
	void roleChecksAllowOnlyTheMatchingRole() throws Exception {
		String seller = registerSeller("9000000008");
		String reseller = registerCustomer("9000000009");

		getWith("/api/v1/test/seller-only", seller).andExpect(status().isOk());
		getWith("/api/v1/test/seller-only", reseller).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		getWith("/api/v1/test/reseller-only", reseller).andExpect(status().isOk());
		getWith("/api/v1/test/reseller-only", seller).andExpect(status().isForbidden());
		getWith("/api/v1/test/anyone-logged-in", seller).andExpect(status().isOk());
		mvc.perform(get("/api/v1/test/anyone-logged-in")).andExpect(status().isUnauthorized());
	}

	@Test
	void adminPathsAreForAdminsOnly() throws Exception {
		String reseller = registerCustomer("9000000010");
		String json = postJson("/api/v1/auth/login", """
				{"phone":"9999999999","password":"admin123"}""").andReturn().getResponse().getContentAsString();
		String admin = JsonPath.read(json, "$.token");

		// No admin endpoints exist yet: a reseller is stopped (403), an admin gets past security (404).
		getWith("/api/v1/admin/anything", reseller).andExpect(status().isForbidden());
		getWith("/api/v1/admin/anything", admin).andExpect(status().isNotFound());
	}

	@Test
	void healthStaysPublic() throws Exception {
		mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
	}

	private String tokenExpiredAnHourAgo() {
		return token(jwtProperties.secret(), Instant.now().minusSeconds(3600));
	}

	private String token(String secret, Instant expiresAt) {
		var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		var claims = JwtClaimsSet.builder()
			.subject("1")
			.claim("role", "ADMIN")
			.issuedAt(expiresAt.minusSeconds(86400))
			.expiresAt(expiresAt)
			.build();
		return new NimbusJwtEncoder(new ImmutableSecret<>(key))
			.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}


	@Test
	void registerAsCustomerReturnsCustomerRole() throws Exception {
		postJson("/api/v1/auth/register", """
				{"phone":"9000000091","name":"Cust","password":"password1","role":"CUSTOMER"}""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.user.role").value("CUSTOMER"));
	}

	@Test
	void registerAsResellerIsRejected() throws Exception {
		postJson("/api/v1/auth/register", """
				{"phone":"9000000092","name":"Old","password":"password1","role":"RESELLER"}""")
			.andExpect(status().isBadRequest());
	}
}
