package com.sareemarket.booking;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.booking.dto.BookingDto;
import com.sareemarket.booking.dto.BookingSummaryDto;
import com.sareemarket.booking.dto.CreateBookingRequest;
import com.sareemarket.common.PageDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
class BookingController {

	private final BookingService service;

	BookingController(BookingService service) {
		this.service = service;
	}

	// ---- reseller ----

	@PostMapping("/items/{itemId}/bookings")
	@PreAuthorize("hasRole('RESELLER')")
	@ResponseStatus(HttpStatus.CREATED)
	BookingDto request(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId,
			@Valid @RequestBody(required = false) CreateBookingRequest body) {
		return service.request(userId(jwt), itemId, body == null ? null : body.note());
	}

	@GetMapping("/bookings/mine")
	@PreAuthorize("hasRole('RESELLER')")
	PageDto<BookingDto> mine(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) BookingFilter status,
			@RequestParam(required = false) BookingStatus state, @RequestParam(defaultValue = "0") int page) {
		return service.mine(userId(jwt), status, state, page);
	}

	@PostMapping("/bookings/{id}/cancel")
	@PreAuthorize("hasRole('RESELLER')")
	BookingDto cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return service.cancel(userId(jwt), id);
	}

	/** The reseller has looked at the status changes: clears the badge. */
	@PostMapping("/bookings/seen")
	@PreAuthorize("hasRole('RESELLER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void seen(@AuthenticationPrincipal Jwt jwt) {
		service.markSeen(userId(jwt));
	}

	// ---- seller ----

	@GetMapping("/bookings/received")
	@PreAuthorize("hasRole('SELLER')")
	PageDto<BookingDto> received(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) BookingFilter status,
			@RequestParam(required = false) BookingStatus state, @RequestParam(defaultValue = "0") int page) {
		return service.received(userId(jwt), status, state, page);
	}

	@PostMapping("/bookings/{id}/accept")
	@PreAuthorize("hasRole('SELLER')")
	BookingDto accept(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return service.accept(userId(jwt), id);
	}

	@PostMapping("/bookings/{id}/reject")
	@PreAuthorize("hasRole('SELLER')")
	BookingDto reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return service.reject(userId(jwt), id);
	}

	@PostMapping("/bookings/{id}/extend")
	@PreAuthorize("hasRole('SELLER')")
	BookingDto extend(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return service.extend(userId(jwt), id);
	}

	@PostMapping("/bookings/{id}/sold")
	@PreAuthorize("hasRole('SELLER')")
	BookingDto sold(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return service.sold(userId(jwt), id);
	}

	// ---- both ----

	@GetMapping("/bookings/summary")
	@PreAuthorize("hasAnyRole('SELLER', 'RESELLER')")
	BookingSummaryDto summary(@AuthenticationPrincipal Jwt jwt) {
		return service.summary("SELLER".equals(jwt.getClaimAsString("role")), userId(jwt));
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
