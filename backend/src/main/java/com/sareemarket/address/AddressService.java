package com.sareemarket.address;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sareemarket.address.dto.AddressDto;
import com.sareemarket.address.dto.AddressRequest;
import com.sareemarket.common.ApiException;

/**
 * Saved delivery addresses. Every write first locks the customer's user row, so two requests from one customer
 * (a double tap, two devices) cannot both pass the limit check or both become the default.
 */
@Service
public class AddressService {

	/** Most addresses one customer may keep. */
	static final int MAX_PER_CUSTOMER = 10;

	/** Columns read for an AddressDto; keep in step with MAPPER. */
	private static final String COLUMNS = "id, name, phone, line1, line2, city, state, pin, is_default";

	private static final RowMapper<AddressDto> MAPPER = (rs, n) -> new AddressDto(rs.getLong("id"),
			rs.getString("name"), rs.getString("phone"), rs.getString("line1"), rs.getString("line2"),
			rs.getString("city"), rs.getString("state"), rs.getString("pin"), rs.getBoolean("is_default"));

	private final NamedParameterJdbcTemplate jdbc;

	AddressService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Customer's addresses, default first, then in creation order. */
	public List<AddressDto> list(long customerId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM address WHERE customer_id = :c ORDER BY is_default DESC, id",
				new MapSqlParameterSource("c", customerId), MAPPER);
	}

	/**
	 * One address of this customer. Public because the order package's HoldService reads it for a request.
	 *
	 * @throws ApiException 404 NOT_FOUND if it does not exist or belongs to someone else (the two cases look the same)
	 */
	public AddressDto get(long customerId, long id) {
		return jdbc.query("SELECT " + COLUMNS + " FROM address WHERE id = :id AND customer_id = :c",
				new MapSqlParameterSource("id", id).addValue("c", customerId), MAPPER)
			.stream()
			.findFirst()
			.orElseThrow(AddressService::notFound);
	}

	/**
	 * Saves a new address. It becomes the default if it is the customer's first or makeDefault is true; the previous
	 * default is cleared first so there is never more than one.
	 *
	 * @throws ApiException 409 ADDRESS_LIMIT when the customer already has 10
	 */
	@Transactional
	public AddressDto create(long customerId, AddressRequest r) {
		lock(customerId);
		Integer count = jdbc.queryForObject("SELECT count(*) FROM address WHERE customer_id = :c",
				new MapSqlParameterSource("c", customerId), Integer.class);
		if (count >= MAX_PER_CUSTOMER) {
			throw new ApiException(HttpStatus.CONFLICT, "ADDRESS_LIMIT",
					"You can save up to " + MAX_PER_CUSTOMER + " addresses; delete one first");
		}
		boolean makeDefault = count == 0 || Boolean.TRUE.equals(r.makeDefault());
		if (makeDefault) {
			clearDefault(customerId);
		}
		Long id = jdbc.queryForObject("""
				INSERT INTO address (customer_id, name, phone, line1, line2, city, state, pin, is_default)
				VALUES (:c, :name, :phone, :line1, :line2, :city, :state, :pin, :def) RETURNING id""",
				fields(r).addValue("c", customerId).addValue("def", makeDefault), Long.class);
		return get(customerId, id);
	}

	/**
	 * Overwrites every field of one address. makeDefault true moves the default to it; otherwise its current default flag
	 * is kept.
	 *
	 * @throws ApiException 404 NOT_FOUND
	 */
	@Transactional
	public AddressDto update(long customerId, long id, AddressRequest r) {
		lock(customerId);
		get(customerId, id);
		boolean makeDefault = Boolean.TRUE.equals(r.makeDefault());
		if (makeDefault) {
			clearDefault(customerId);
		}
		jdbc.update("""
				UPDATE address SET name = :name, phone = :phone, line1 = :line1, line2 = :line2, city = :city,
				       state = :state, pin = :pin, is_default = is_default OR :def
				WHERE id = :id AND customer_id = :c""",
				fields(r).addValue("id", id).addValue("c", customerId).addValue("def", makeDefault));
		return get(customerId, id);
	}

	/**
	 * Deletes one address. If the deleted address was the default, the most recently created remaining address is made
	 * the default, so a customer who still has addresses always has a default.
	 *
	 * @throws ApiException 404 NOT_FOUND
	 */
	@Transactional
	public void delete(long customerId, long id) {
		lock(customerId);
		List<Boolean> wasDefault = jdbc.queryForList(
				"DELETE FROM address WHERE id = :id AND customer_id = :c RETURNING is_default",
				new MapSqlParameterSource("id", id).addValue("c", customerId), Boolean.class);
		if (wasDefault.isEmpty()) {
			throw notFound();
		}
		if (wasDefault.get(0)) {
			jdbc.update("""
					UPDATE address SET is_default = TRUE
					WHERE id = (SELECT id FROM address WHERE customer_id = :c ORDER BY created_at DESC, id DESC LIMIT 1)""",
					new MapSqlParameterSource("c", customerId));
		}
	}

	/** Takes a row lock on the customer's user row until the transaction ends, so writes for one customer run one after another. */
	private void lock(long customerId) {
		jdbc.queryForList("SELECT id FROM app_user WHERE id = :c FOR UPDATE",
				new MapSqlParameterSource("c", customerId), Long.class);
	}

	/** Turns off the default flag on all of the customer's addresses. */
	private void clearDefault(long customerId) {
		jdbc.update("UPDATE address SET is_default = FALSE WHERE customer_id = :c AND is_default",
				new MapSqlParameterSource("c", customerId));
	}

	/** Named parameters for the address columns. Text is trimmed and a blank line2 is stored as null. */
	private static MapSqlParameterSource fields(AddressRequest r) {
		String line2 = r.line2() == null || r.line2().isBlank() ? null : r.line2().strip();
		return new MapSqlParameterSource("name", r.name().strip()).addValue("phone", r.phone())
			.addValue("line1", r.line1().strip())
			.addValue("line2", line2)
			.addValue("city", r.city().strip())
			.addValue("state", r.state().strip())
			.addValue("pin", r.pin());
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Address not found");
	}

}
