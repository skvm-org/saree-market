package com.sareemarket.common;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gives every request an id and logs one line when it ends: method, path, status, time taken and who asked. It runs
 * before Spring Security, so rejected logins (401) and forbidden calls (403) are in the log too. The id goes back to
 * the client in X-Request-Id and in error bodies, so a failed call on a phone can be found in the log. The query
 * string is left out on purpose: it can hold search words and other things nobody needs in a log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

	// A client may send its own id, but only a plain one, so it cannot put line breaks or markup into the log.
	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return "/api/v1/health".equals(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String id = request.getHeader(RequestContext.REQUEST_ID_HEADER);
		if (id == null || !SAFE_ID.matcher(id).matches()) {
			id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		}
		MDC.put(RequestContext.REQUEST_ID, id);
		response.setHeader(RequestContext.REQUEST_ID_HEADER, id);
		long started = System.nanoTime();
		try {
			chain.doFilter(request, response);
		}
		finally {
			long millis = (System.nanoTime() - started) / 1_000_000;
			int status = response.getStatus();
			String user = MDC.get(RequestContext.USER_ID);
			String role = MDC.get(RequestContext.ROLE);
			log.atLevel(levelFor(status))
				.addKeyValue("method", request.getMethod())
				.addKeyValue("path", request.getRequestURI())
				.addKeyValue("status", status)
				.addKeyValue("durationMs", millis)
				.addKeyValue("clientIp", request.getRemoteAddr())
				.log("{} {} {} {}ms user={}{}", request.getMethod(), request.getRequestURI(), status, millis,
						user == null ? "-" : user, role == null ? "" : " " + role);
			MDC.clear();
		}
	}

	static Level levelFor(int status) {
		if (status >= 500) {
			return Level.ERROR;
		}
		return status == 401 || status == 403 || status == 429 ? Level.WARN : Level.INFO;
	}

}
