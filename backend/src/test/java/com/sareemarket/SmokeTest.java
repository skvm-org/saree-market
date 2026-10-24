package com.sareemarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SmokeTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void healthIsUp() throws Exception {
		mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void flywayCreatedAllTables() {
		var tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
				String.class);
		assertThat(tables).contains("app_user", "seller_profile", "saree_type", "fabric", "collection", "item",
				"seller_follow", "notification");
	}

	@Test
	void seedDataIsPresent() {
		assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE phone = '9999999999'", String.class))
			.isEqualTo("ADMIN");
		assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE phone = '9999999999'", String.class))
			.startsWith("$2a$10$");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM saree_type WHERE name = 'Kanjivaram'", Integer.class))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM fabric", Integer.class)).isPositive();
	}

	@Test
	void searchVectorCoversCollectionTextAndItemColour() {
		jdbc.update("INSERT INTO app_user (phone, password_hash, name, role) VALUES ('1111111111', 'x', 'S', 'SELLER')");
		Long sellerId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = '1111111111'", Long.class);
		jdbc.update("INSERT INTO seller_profile VALUES (?, 'Shop', 'Market', '1111111111', NULL)", sellerId);
		jdbc.update("""
				INSERT INTO collection (seller_id, title, saree_type_id, fabric_id, price, description)
				VALUES (?, 'Diwali soft silks', 1, 1, 2500, 'festive wear')""", sellerId);
		Long collectionId = jdbc.queryForObject("SELECT max(id) FROM collection", Long.class);
		jdbc.update("""
				INSERT INTO item (collection_id, seller_id, colour, image_path, thumb_path, image_hash)
				VALUES (?, ?, 'maroon', 'a', 'b', 1)""", collectionId, sellerId);

		// Other tests share this database, so only look at the item created here.
		Long itemId = jdbc.queryForObject("SELECT max(id) FROM item", Long.class);
		String match = "SELECT count(*) FROM item WHERE id = " + itemId + " AND search_vector @@ plainto_tsquery('simple', ?)";
		assertThat(jdbc.queryForObject(match, Integer.class, "maroon")).isEqualTo(1);
		assertThat(jdbc.queryForObject(match, Integer.class, "festive")).isEqualTo(1);

		jdbc.update("UPDATE collection SET title = 'Pongal cottons' WHERE id = ?", collectionId);
		assertThat(jdbc.queryForObject(match, Integer.class, "pongal")).isEqualTo(1);
		assertThat(jdbc.queryForObject(match, Integer.class, "diwali")).isZero();
	}

}
