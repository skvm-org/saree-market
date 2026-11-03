package com.sareemarket.common;

import java.util.Map;

import org.slf4j.MDC;

/** The body of every error. requestId matches the X-Request-Id header and the log lines of the same request. */
public record ApiError(String code, String message, Map<String, String> fields, String requestId) {

	/** Fills requestId from the current request's log context, so callers building an error by hand do not pass it. */
	public ApiError(String code, String message, Map<String, String> fields) {
		this(code, message, fields, MDC.get(RequestContext.REQUEST_ID));
	}

}
