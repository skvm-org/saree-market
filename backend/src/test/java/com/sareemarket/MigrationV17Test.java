package com.sareemarket;

import static org.assertj.core.api.Assertions.assertThat;

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
 * V17 moves orders off the retired statuses. It is rehearsed on a database built the way production is built: every
 * migration up to V16, one order in each status (with the history rows the old flow wrote), then V17.
 */
class MigrationV17Test {

	static PostgreSQLContainer db;

	static long submitted;

	static long rejected;

	static long packed;

	static long confirmed;

	static long shipped;

	static long delivered;

	static long cancelled;

	static long stockedSaree;

	static long cappedSaree;

	static long rejectedWithDeletedSaree;

	@BeforeAll
	static void buildProductionShapedDatabase() throws Exception {
		db = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
		db.start();
		flyway("16").migrate();
		try (Connection c = connect(); Statement s = c.createStatement()) {
			s.execute("INSERT INTO app_user (phone, password_hash, name, role) VALUES ('9100000001', 'x', 'Lakshmi', 'SELLER')");
			s.execute("INSERT INTO app_user (phone, password_hash, name, role) VALUES ('9100000002', 'x', 'Priya', 'CUSTOMER')");
			s.execute("INSERT INTO seller_profile (user_id, shop_name, market_location, contact_phone) "
					+ "SELECT id, 'Lakshmi Silks', 'Dindigul', '9100000001' FROM app_user WHERE phone = '9100000001'");
			s.execute("INSERT INTO collection (seller_id, title, price) "
					+ "SELECT id, 'Diwali silks', 2500 FROM app_user WHERE phone = '9100000001'");
			stockedSaree = item(s, 4);
			cappedSaree = item(s, 9999);
			submitted = order(s, "PAYMENT_SUBMITTED", "PAYMENT_SUBMITTED");
			rejected = order(s, "PAYMENT_REJECTED", "PAYMENT_SUBMITTED", "PAYMENT_REJECTED");
			packed = order(s, "PACKED", "PAYMENT_SUBMITTED", "CONFIRMED", "PACKED");
			confirmed = order(s, "CONFIRMED", "PAYMENT_SUBMITTED", "CONFIRMED");
			shipped = order(s, "SHIPPED", "PAYMENT_SUBMITTED", "CONFIRMED", "PACKED", "SHIPPED");
			delivered = order(s, "DELIVERED", "PAYMENT_SUBMITTED", "CONFIRMED", "PACKED", "SHIPPED", "DELIVERED");
			cancelled = order(s, "CANCELLED", "PAYMENT_SUBMITTED", "CANCELLED");
			// The rejected order holds 2 pieces of stockedSaree and 3 of cappedSaree (9999 + 3 must cap at 10000).
			line(s, rejected, stockedSaree, 2);
			line(s, rejected, cappedSaree, 3);
			// An order whose saree was deleted since: its line has no item and must not break the migration.
			rejectedWithDeletedSaree = order(s, "PAYMENT_REJECTED", "PAYMENT_SUBMITTED", "PAYMENT_REJECTED");
			s.execute("INSERT INTO order_line (order_id, item_id, title, unit_price, quantity) VALUES ("
					+ rejectedWithDeletedSaree + ", NULL, 'Gone', 2500, 1)");
			// A live order of the same saree is untouched.
			line(s, confirmed, stockedSaree, 1);
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

	static long item(Statement s, int quantity) throws SQLException {
		try (ResultSet rs = s.executeQuery("INSERT INTO item (collection_id, seller_id, image_path, thumb_path, image_hash, status, quantity) "
				+ "SELECT c.id, c.seller_id, 'a', 'b', 1, 'AVAILABLE', " + quantity + " FROM collection c RETURNING id")) {
			rs.next();
			return rs.getLong(1);
		}
	}

	/** An order in `status`, with one history row per name in `history`. */
	static long order(Statement s, String status, String... history) throws SQLException {
		long id;
		try (ResultSet rs = s.executeQuery("INSERT INTO customer_order (customer_id, seller_id, status, items_total, shipping_fee, grand_total, address, "
				+ "seller_unread, customer_unread) "
				+ "SELECT c.id, sp.user_id, '" + status + "', 2500, 60, 2560, '{}'::jsonb, FALSE, FALSE "
				+ "FROM app_user c, seller_profile sp WHERE c.phone = '9100000002' RETURNING id")) {
			rs.next();
			id = rs.getLong(1);
		}
		for (String entry : history) {
			s.execute("INSERT INTO order_status_history (order_id, status) VALUES (" + id + ", '" + entry + "')");
		}
		return id;
	}

	static void line(Statement s, long order, long item, int quantity) throws SQLException {
		s.execute("INSERT INTO order_line (order_id, item_id, title, unit_price, quantity) VALUES (" + order + ", " + item
				+ ", 'Saree', 2500, " + quantity + ")");
	}

	static String text(String sql) throws SQLException {
		try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
	}

	static String status(long order) throws SQLException {
		return text("SELECT status FROM customer_order WHERE id = " + order);
	}

	static String history(long order) throws SQLException {
		return text("SELECT string_agg(status, ',' ORDER BY id) FROM order_status_history WHERE order_id = " + order);
	}

	@Test
	void ordersAwaitingPaymentCheckAndPackedOrdersBecomeConfirmed() throws Exception {
		assertThat(status(submitted)).isEqualTo("CONFIRMED");
		assertThat(status(packed)).isEqualTo("CONFIRMED");
	}

	@Test
	void awaitingOrderGetsAConfirmedHistoryRowAndTheSellerIsTold() throws Exception {
		assertThat(history(submitted)).isEqualTo("PAYMENT_SUBMITTED,CONFIRMED");
		assertThat(text("SELECT seller_unread FROM customer_order WHERE id = " + submitted)).isEqualTo("t");
	}

	@Test
	void packedOrderKeepsItsHistoryAndGetsNoDuplicateConfirmedRow() throws Exception {
		assertThat(history(packed)).isEqualTo("PAYMENT_SUBMITTED,CONFIRMED,PACKED");
	}

	@Test
	void rejectedOrdersAreCancelledBySystemAndTheCustomerIsTold() throws Exception {
		assertThat(status(rejected)).isEqualTo("CANCELLED");
		assertThat(status(rejectedWithDeletedSaree)).isEqualTo("CANCELLED");
		assertThat(text("SELECT cancelled_by FROM customer_order WHERE id = " + rejected)).isEqualTo("SYSTEM");
		assertThat(text("SELECT cancel_reason IS NOT NULL FROM customer_order WHERE id = " + rejected)).isEqualTo("t");
		assertThat(text("SELECT customer_unread FROM customer_order WHERE id = " + rejected)).isEqualTo("t");
		assertThat(text("SELECT payment_deadline IS NULL FROM customer_order WHERE id = " + rejected)).isEqualTo("t");
		assertThat(history(rejected)).isEqualTo("PAYMENT_SUBMITTED,PAYMENT_REJECTED,CANCELLED");
	}

	@Test
	void rejectedOrdersPutTheirStockBackWithTheSameCapAsACancel() throws Exception {
		assertThat(text("SELECT quantity FROM item WHERE id = " + stockedSaree)).isEqualTo("6");
		assertThat(text("SELECT quantity FROM item WHERE id = " + cappedSaree)).isEqualTo("10000");
	}

	@Test
	void ordersInTheCurrentStatusesAreUntouched() throws Exception {
		assertThat(status(confirmed)).isEqualTo("CONFIRMED");
		assertThat(history(confirmed)).isEqualTo("PAYMENT_SUBMITTED,CONFIRMED");
		assertThat(text("SELECT seller_unread FROM customer_order WHERE id = " + confirmed)).isEqualTo("f");
		assertThat(status(shipped)).isEqualTo("SHIPPED");
		assertThat(status(delivered)).isEqualTo("DELIVERED");
		assertThat(status(cancelled)).isEqualTo("CANCELLED");
		assertThat(text("SELECT cancelled_by IS NULL FROM customer_order WHERE id = " + cancelled)).isEqualTo("t");
	}

	@Test
	void noOrderIsLeftInARetiredStatus() throws Exception {
		assertThat(text("SELECT count(*) FROM customer_order WHERE status IN ('PAYMENT_SUBMITTED', 'PAYMENT_REJECTED', 'PACKED')"))
			.isEqualTo("0");
	}

	@Test
	void theStatusConstraintStillAcceptsRetiredValuesSoThePreviousReleaseCanRunOnTheData() throws Exception {
		try (Connection c = connect(); Statement s = c.createStatement()) {
			s.execute("UPDATE customer_order SET status = 'PACKED' WHERE id = " + packed);
			s.execute("UPDATE customer_order SET status = 'CONFIRMED' WHERE id = " + packed);
		}
	}

	@Test
	void flywayHistoryReachesV17() throws Exception {
		assertThat(Integer.parseInt(text("SELECT max(version::int)::text FROM flyway_schema_history WHERE success")))
			.isGreaterThanOrEqualTo(17);
	}

}
