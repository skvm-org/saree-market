package com.sareemarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V13 drops the booking feature. It is rehearsed here on a database built the way production was built: every
 * migration up to V12, real-looking rows (held sarees, open and finished requests, an order), then V13.
 */
class MigrationV13Test {

	static PostgreSQLContainer db;

	static long heldWithNoPieces;

	static long heldWithPieces;

	static long available;

	static long soldOut;

	static long users;

	static long items;

	static long orders;

	static long cartLines;

	@BeforeAll
	static void buildProductionShapedDatabase() throws Exception {
		db = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
		db.start();
		flyway("12").migrate();
		try (Connection c = connect(); Statement s = c.createStatement()) {
			s.execute("INSERT INTO app_user (phone, password_hash, name, role) VALUES ('9100000001', 'x', 'Lakshmi', 'SELLER')");
			s.execute("INSERT INTO app_user (phone, password_hash, name, role) VALUES ('9100000002', 'x', 'Priya', 'CUSTOMER')");
			s.execute("INSERT INTO app_user (phone, password_hash, name, role) VALUES ('9100000003', 'x', 'Divya', 'CUSTOMER')");
			s.execute("INSERT INTO seller_profile (user_id, shop_name, market_location, contact_phone) "
					+ "SELECT id, 'Lakshmi Silks', 'Dindigul', '9100000001' FROM app_user WHERE phone = '9100000001'");
			s.execute("INSERT INTO collection (seller_id, title, saree_type_id, fabric_id, price) "
					+ "SELECT id, 'Diwali silks', 1, 1, 2500 FROM app_user WHERE phone = '9100000001'");
			available = item(s, "AVAILABLE", 5);
			soldOut = item(s, "SOLD_OUT", 0);
			heldWithNoPieces = item(s, "ON_HOLD", 0); // held when V7 zeroed the held ones
			heldWithPieces = item(s, "ON_HOLD", 3); // held after V7: the count was never touched
			booking(s, available, "9100000002", "PENDING", false);
			booking(s, heldWithNoPieces, "9100000002", "ACCEPTED", true);
			booking(s, heldWithPieces, "9100000003", "ACCEPTED", true);
			booking(s, soldOut, "9100000003", "SOLD", false);
			s.execute("INSERT INTO cart_line (customer_id, item_id, quantity) SELECT id, " + available
					+ ", 1 FROM app_user WHERE phone = '9100000002'");
			s.execute("INSERT INTO customer_order (customer_id, seller_id, status, items_total, shipping_fee, grand_total, address) "
					+ "SELECT c.id, sp.user_id, 'PAYMENT_SUBMITTED', 2500, 60, 2560, '{}'::jsonb FROM app_user c, seller_profile sp "
					+ "WHERE c.phone = '9100000002'");
			users = count(s, "app_user");
			items = count(s, "item");
			orders = count(s, "customer_order");
			cartLines = count(s, "cart_line");
		}
		flyway(null).migrate();
	}

	@AfterAll
	static void stop() {
		db.stop();
	}

	static Flyway flyway(String target) {
		var config = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).locations("classpath:db/migration")
			.placeholders(java.util.Map.of("admin_password_hash", "$2a$10$ITlk.H9M6TpJyAkyBlzko.vPfpfZfSY8QQaGOPKE772dc6ZlNtGLS"));
		return (target == null ? config : config.target(target)).load();
	}

	static Connection connect() throws SQLException {
		return DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
	}

	static long item(Statement s, String status, int quantity) throws SQLException {
		try (ResultSet rs = s.executeQuery("INSERT INTO item (collection_id, seller_id, image_path, thumb_path, image_hash, status, quantity) "
				+ "SELECT c.id, c.seller_id, 'a', 'b', 1, '" + status + "', " + quantity + " FROM collection c RETURNING id")) {
			rs.next();
			return rs.getLong(1);
		}
	}

	static void booking(Statement s, long item, String customerPhone, String status, boolean held) throws SQLException {
		s.execute("INSERT INTO booking_request (item_id, reseller_id, seller_id, status, hold_expires_at) "
				+ "SELECT " + item + ", u.id, i.seller_id, '" + status + "', " + (held ? "now() + interval '20 hours'" : "NULL")
				+ " FROM app_user u, item i WHERE u.phone = '" + customerPhone + "' AND i.id = " + item);
	}

	static long count(Statement s, String table) throws SQLException {
		try (ResultSet rs = s.executeQuery("SELECT count(*) FROM " + table)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	static String text(String sql) throws SQLException {
		try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
	}

	@Test
	void heldSareesComeBackOnSaleWithAtLeastOnePiece() throws Exception {
		assertThat(text("SELECT status || ':' || quantity FROM item WHERE id = " + heldWithNoPieces)).isEqualTo("AVAILABLE:1");
		assertThat(text("SELECT status || ':' || quantity FROM item WHERE id = " + heldWithPieces)).isEqualTo("AVAILABLE:3");
	}

	@Test
	void availableAndSoldOutItemsAreUntouched() throws Exception {
		assertThat(text("SELECT status || ':' || quantity FROM item WHERE id = " + available)).isEqualTo("AVAILABLE:5");
		assertThat(text("SELECT status || ':' || quantity FROM item WHERE id = " + soldOut)).isEqualTo("SOLD_OUT:0");
	}

	@Test
	void noItemIsOnHoldAnyMore() throws Exception {
		assertThat(text("SELECT count(*) FROM item WHERE status = 'ON_HOLD'")).isEqualTo("0");
	}

	@Test
	void bookingTableIsGone() throws Exception {
		assertThat(text("SELECT to_regclass('booking_request')::text")).isNull();
	}

	@Test
	void itemStatusNowRejectsOnHold() throws Exception {
		try (Connection c = connect(); Statement s = c.createStatement()) {
			assertThatThrownBy(() -> s.execute("UPDATE item SET status = 'ON_HOLD' WHERE id = " + available))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("item_status_check");
		}
	}

	@Test
	void otherTablesKeepTheirRows() throws Exception {
		try (Connection c = connect(); Statement s = c.createStatement()) {
			assertThat(count(s, "app_user")).isEqualTo(users);
			assertThat(count(s, "item")).isEqualTo(items);
			assertThat(count(s, "customer_order")).isEqualTo(orders);
			assertThat(count(s, "cart_line")).isEqualTo(cartLines);
		}
	}

	@Test
	void flywayHistoryEndsAtV13() throws Exception {
		assertThat(text("SELECT max(version::int)::text FROM flyway_schema_history WHERE success")).isEqualTo("13");
	}

}
