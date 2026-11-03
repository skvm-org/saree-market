package com.sareemarket.notification;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.common.PageDto;
import com.sareemarket.notification.dto.NotificationDto;
import com.sareemarket.notification.dto.UnreadCountDto;

/**
 * The notifications list of the signed-in reseller (role CUSTOMER). Every call is scoped to the user in the JWT,
 * so one customer can never read or change another customer's notifications. Other roles get 403.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('CUSTOMER')")
class NotificationController {

	private final NotificationService service;

	NotificationController(NotificationService service) {
		this.service = service;
	}

	/**
	 * GET /api/v1/notifications?page=0: one page of the caller's notifications, most recently changed first.
	 * Returns 400 when page is negative.
	 */
	@GetMapping
	PageDto<NotificationDto> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page) {
		return service.list(userId(jwt), page);
	}

	/**
	 * GET /api/v1/notifications/unread-count: how many unread notifications the caller has, for the bell badge.
	 */
	@GetMapping("/unread-count")
	UnreadCountDto unreadCount(@AuthenticationPrincipal Jwt jwt) {
		return new UnreadCountDto(service.unreadCount(userId(jwt)));
	}

	/**
	 * POST /api/v1/notifications/{id}/read: marks one notification read. Returns 204; 404 NOT_FOUND when it does
	 * not exist or belongs to someone else.
	 */
	@PostMapping("/{id}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		service.markRead(userId(jwt), id);
	}

	/**
	 * POST /api/v1/notifications/read-all: marks every unread notification of the caller read. Returns 204.
	 */
	@PostMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markAllRead(@AuthenticationPrincipal Jwt jwt) {
		service.markAllRead(userId(jwt));
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
