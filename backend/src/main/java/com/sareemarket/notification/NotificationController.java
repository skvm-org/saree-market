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

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('RESELLER')")
class NotificationController {

	private final NotificationService service;

	NotificationController(NotificationService service) {
		this.service = service;
	}

	@GetMapping
	PageDto<NotificationDto> list(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page) {
		return service.list(userId(jwt), page);
	}

	@GetMapping("/unread-count")
	UnreadCountDto unreadCount(@AuthenticationPrincipal Jwt jwt) {
		return new UnreadCountDto(service.unreadCount(userId(jwt)));
	}

	@PostMapping("/{id}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		service.markRead(userId(jwt), id);
	}

	@PostMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markAllRead(@AuthenticationPrincipal Jwt jwt) {
		service.markAllRead(userId(jwt));
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
