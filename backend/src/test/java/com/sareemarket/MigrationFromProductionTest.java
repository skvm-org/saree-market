package com.sareemarket;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Production runs the version before the customer-orders work: migrations V1 to V5, with resellers, held sarees and
 * booking requests. The next deploy applies V6 to V13 in one go. This test does exactly that on a database shaped like
 * production's, so a surprise shows up here and not on the live server.
 */
class MigrationFromProductionTest {

	static PostgreSQLContainer db;

	static long available;

	static long soldOut;

	static long heldWithRequest;

	static long followCount;

	static long notificationCount;

	@BeforeAll
	static void buildTheDatabaseAsProductionIs() throws Exception {
		db = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
		db.start();
		flyway("5").migrate();
		try (Connection c = connect(); Statement s = c.createStatement()) {
			s.execute("INSERT INTO app_user (phone, password_hash, name, role) VALUES ('9200000001', 'x', 'Lakshmi', 'SELLER')");
			s.execute("INSERT INTO app_user (phone, password_hash, name, role) VALUES ('9200000002', 'x', 'Priya', 'RESELLER')");
			s.execute("INSERT INTO app_user (phone, password_hash, name, role) VALUES ('9200000003', 'x', 'Divya', 'RESELLER')");
			s.execute("INSERT INTO seller_profile (user_id, shop_name, market_location, contact_phone) "
					+ "SELECT id, 'Lakshmi Silks', 'Dindigul', '9200000001' FROM app_user WHERE phone = '9200000001'");
			s.execute("INSERT INTO collection (seller_id, title, saree_type_id, fabric_id, price) "
					+ "SELECT id, 'Diwali silks', 1, 1, 2500 FROM app_user WHERE phone = '9200000001'");
			available = item(s, "AVAILABLE");
			soldOut = item(s, "SOLD_OUT");
			heldWithRequest = item(s, "ON_HOLD");
			long pending = item(s, "AVAILABLE");
			booking(s, pending, "9200000002", "PENDING", false);
			booking(s, heldWithRequest, "9200000003", "ACCEPTED", true);
			booking(s, soldOut, "9200000002", "SOLD", false);
			s.execute("INSERT INTO seller_follow (reseller_id, seller_id) SELECT r.id, sp.user_id FROM app_user r, seller_profile sp "
					+ "WHERE r.phone = '9200000002'");
			s.execute("INSERT INTO notification (reseller_id, seller_id, collection_id, type, item_count) "
					+ "SELECT r.id, sp.user_id, c.id, 'NEW_COLLECTION', 4 FROM app_user r, seller_profile sp, collection c "
					+ "WHERE r.phone = '9200000002'");
			followCount = count(s, "seller_follow");
			notificationCount = count(s, "notification");
		}
		flyway(null).migrate();
	}

	@AfterAll
	static void stop() {
		db.stop();
	}

	static Flyway flyway(String target) {
		var config = Flyway.configure()
			.dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
			.locations("classpath:db/migration")
			.placeholders(java.util.Map.of("admin_password_hash", "$2a$10$ITlk.H9M6TpJyAkyBlzko.vPfpfZfSY8QQaGOPKE772dc6ZlNtGLS"));
		return (target == null ? config : config.target(target)).load();
	}

	static Connection connect() throws SQLException {
		return DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
	}

	static long item(Statement s, String status) throws SQLException {
		try (ResultSet rs = s.executeQuery("INSERT INTO item (collection_id, seller_id, image_path, thumb_path, image_hash, status) "
				+ "SELECT c.id, c.seller_id, 'a', 'b', 1, '" + status + "' FROM collection c RETURNING id")) {
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
	void resellersBecomeCustomers() throws Exception {
		assertThat(text("SELECT count(*) FROM app_user WHERE role = 'RESELLER'")).isEqualTo("0");
		assertThat(text("SELECT count(*) FROM app_user WHERE role = 'CUSTOMER'")).isEqualTo("2");
		assertThat(text("SELECT role FROM app_user WHERE phone = '9200000001'")).isEqualTo("SELLER");
		assertThat(text("SELECT role FROM app_user WHERE phone = '9999999999'")).isEqualTo("ADMIN");
	}

	@Test
	void theHeldSareeComesBackOnSaleWithOnePiece() throws Exception {
		assertThat(text("SELECT status || ':' || quantity FROM item WHERE id = " + heldWithRequest)).isEqualTo("AVAILABLE:1");
	}

	@Test
	void otherSareesKeepTheirMarkAndGetAQuantity() throws Exception {
		// V7 gives every available saree one piece and every sold out one none
		assertThat(text("SELECT status || ':' || quantity FROM item WHERE id = " + available)).isEqualTo("AVAILABLE:1");
		assertThat(text("SELECT status || ':' || quantity FROM item WHERE id = " + soldOut)).isEqualTo("SOLD_OUT:0");
	}

	@Test
	void bookingsAreGoneAndNothingIsHeld() throws Exception {
		assertThat(text("SELECT to_regclass('booking_request')::text")).isNull();
		assertThat(text("SELECT count(*) FROM item WHERE status = 'ON_HOLD'")).isEqualTo("0");
	}

	@Test
	void followsAndNotificationsSurvive() throws Exception {
		try (Connection c = connect(); Statement s = c.createStatement()) {
			assertThat(count(s, "seller_follow")).isEqualTo(followCount);
			assertThat(count(s, "notification")).isEqualTo(notificationCount);
		}
	}

	@Test
	void everyMigrationFromSixToThirteenRanOnce() throws Exception {
		List<String> versions = new java.util.ArrayList<>();
		try (Connection c = connect(); Statement s = c.createStatement();
				ResultSet rs = s.executeQuery("SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")) {
			while (rs.next()) {
				versions.add(rs.getString(1));
			}
		}
		assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");
	}

}
