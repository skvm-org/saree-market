package com.sareemarket.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * An expected failure that the caller should see: a wrong password, a missing record, a rule that was broken. Services
 * throw it and {@link ApiExceptionHandler} turns it into the HTTP status and the {@link ApiError} body, so controllers
 * never build error responses themselves.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private final Map<String, String> fields;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, Map.of());
	}

	/**
	 * @param code short machine-readable name that clients branch on (for example {@code NOT_FOUND})
	 * @param message sentence a person can read
	 * @param fields field name to problem, for validation errors; empty when the error is not about one field
	 */
	public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
		super(message);
		this.status = status;
		this.code = code;
		this.fields = fields;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public Map<String, String> fields() {
		return fields;
	}

}
