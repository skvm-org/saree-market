package com.sareemarket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** Same annotations as the other integration tests, so they all share one application and one PostgreSQL. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public abstract class IntegrationTestBase {

	private static final AtomicLong PHONES = new AtomicLong(7_000_000_000L);

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	/** A phone number nobody else in this test run has used. */
	protected static String newPhone() {
		return String.valueOf(PHONES.incrementAndGet());
	}

	protected String registerSeller() throws Exception {
		String phone = newPhone();
		return token("""
				{"phone":"%s","name":"Lakshmi","password":"password1","role":"SELLER",
				 "shopName":"Lakshmi Silks","marketLocation":"Dindigul","contactPhone":"%s"}""".formatted(phone, phone));
	}

	protected String registerReseller() throws Exception {
		return token("""
				{"phone":"%s","name":"Priya","password":"password1","role":"RESELLER"}""".formatted(newPhone()));
	}

	private String token(String json) throws Exception {
		String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	protected ResultActions getWith(String path, String token) throws Exception {
		return mvc.perform(get(path).header("Authorization", "Bearer " + token));
	}

}
