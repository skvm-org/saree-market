package com.sareemarket.common;

/** Names of the values every log line of one request carries (the MDC) and the header that names the request. */
public final class RequestContext {

	public static final String REQUEST_ID = "requestId";

	public static final String USER_ID = "userId";

	public static final String ROLE = "role";

	public static final String REQUEST_ID_HEADER = "X-Request-Id";

	private RequestContext() {
	}

}
