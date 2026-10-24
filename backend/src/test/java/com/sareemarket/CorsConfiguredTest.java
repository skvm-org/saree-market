package com.sareemarket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:4200")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CorsConfiguredTest {

	@Autowired
	MockMvc mvc;

	@Test
	void allowsConfiguredOriginOnly() throws Exception {
		mvc.perform(options("/api/v1/health")
			.header("Origin", "http://localhost:4200")
			.header("Access-Control-Request-Method", "GET"))
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
		mvc.perform(options("/api/v1/health")
			.header("Origin", "http://evil.example")
			.header("Access-Control-Request-Method", "GET"))
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

}
