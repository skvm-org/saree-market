package com.sareemarket.common;

import java.util.Map;

import org.slf4j.MDC;

/** The body of every error. requestId matches the X-Request-Id header and the log lines of the same request. */
public record ApiError(String code, String message, Map<String, String> fields, String requestId) {

	public ApiError(String code, String message, Map<String, String> fields) {
		this(code, message, fields, MDC.get(RequestContext.REQUEST_ID));
	}

}
