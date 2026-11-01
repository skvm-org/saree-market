package com.sareemarket.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns every failure into the standard error body and leaves a log line saying why. The request line written by
 * RequestLogFilter says what was asked and the status; the line here says what went wrong, with the same request id.
 * Only a real server fault (500) gets a stack trace.
 */
@RestControllerAdvice
class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApi(ApiException e, HttpServletRequest request) {
		if (e.status().is5xxServerError()) {
			log.error("{} {} failed with {}: {}", request.getMethod(), request.getRequestURI(), e.code(), e.getMessage(), e);
		}
		else {
			log.atLevel(clientErrorLevel(e.status().value()))
				.log("{} {} refused with {}: {}{}", request.getMethod(), request.getRequestURI(), e.code(),
						e.getMessage(), e.fields().isEmpty() ? "" : " fields=" + e.fields().keySet());
		}
		return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage(), e.fields()));
	}

	// Method security (@PreAuthorize) throws these from inside a controller, where this advice would otherwise catch them.
	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiError> handleDenied(AccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(new ApiError("FORBIDDEN", "You do not have access to this", Map.of()));
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ApiError> handleUnauthenticated(AuthenticationException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("UNAUTHORIZED", "Login required", Map.of()));
	}

	@ExceptionHandler(TypeMismatchException.class)
	ResponseEntity<ApiError> handleTypeMismatch(TypeMismatchException e) {
		return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "A value in the request has the wrong type", Map.of()));
	}

	@ExceptionHandler(MultipartException.class)
	ResponseEntity<ApiError> handleMultipart(MultipartException e, HttpServletRequest request) {
		log.warn("{} {} has an unreadable upload: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
		return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "The upload could not be read", Map.of()));
	}

	/**
	 * Everything else. Spring's own client errors (unknown route, wrong method, missing parameter) keep their status and
	 * headers; anything else is a bug or an outage: log it with the stack trace and tell the client only that something
	 * went wrong and which request to ask about.
	 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest request) {
		if (e instanceof ErrorResponse er && !er.getStatusCode().is5xxServerError()) {
			HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
			String detail = er.getBody().getDetail();
			return ResponseEntity.status(status)
				.headers(er.getHeaders())
				.body(new ApiError(status.name(), detail != null ? detail : status.getReasonPhrase(), Map.of()));
		}
		log.error("Unhandled {} on {} {}", e.getClass().getSimpleName(), request.getMethod(), request.getRequestURI(), e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(new ApiError("INTERNAL_ERROR", "Something went wrong. Please try again.", Map.of()));
	}

	// Field names only: the values are what the user typed, and may be private.
	private static void logInvalid(HttpServletRequest request, Map<String, String> fields) {
		log.info("{} {} has invalid fields {}", request.getMethod(), request.getRequestURI(), fields.keySet());
	}

	private static org.slf4j.event.Level clientErrorLevel(int status) {
		return status == 401 || status == 403 || status == 429 || status == 409 ? org.slf4j.event.Level.WARN
				: org.slf4j.event.Level.INFO;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleInvalid(MethodArgumentNotValidException e, HttpServletRequest request) {
		Map<String, String> fields = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors().forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
		logInvalid(request, fields);
		return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Some fields are invalid", fields));
	}

	// Constraints placed directly on a controller parameter, such as @Min on ?page=.
	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ApiError> handleParameterInvalid(HandlerMethodValidationException e, HttpServletRequest request) {
		Map<String, String> fields = new LinkedHashMap<>();
		e.getParameterValidationResults().forEach(result -> {
			String name = result.getMethodParameter().getParameterName();
			result.getResolvableErrors().forEach(error -> fields.putIfAbsent(name == null ? "parameter" : name, error.getDefaultMessage()));
		});
		logInvalid(request, fields);
		return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Some fields are invalid", fields));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
		log.info("{} {} has a malformed body", request.getMethod(), request.getRequestURI());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ApiError("BAD_REQUEST", "Malformed request body", Map.of()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
			.body(new ApiError("PAYLOAD_TOO_LARGE", "Each photo must be 10 MB or smaller", Map.of()));
	}

}
