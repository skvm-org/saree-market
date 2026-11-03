package com.sareemarket.auth;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sareemarket.common.RequestContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs after the token has been checked and tells the log who is calling. The request log filter, which wraps this
 * one, writes the id and role on its closing line and clears them afterwards.
 */
class UserContextFilter extends OncePerRequestFilter {

	/** Copies the user id and role from the verified token into the logging context. Does not authenticate or reject anything. */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
			MDC.put(RequestContext.USER_ID, jwt.getSubject());
			MDC.put(RequestContext.ROLE, jwt.getClaimAsString("role"));
		}
		chain.doFilter(request, response);
	}

}
