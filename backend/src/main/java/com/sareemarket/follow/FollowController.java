package com.sareemarket.follow;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.follow.dto.FollowDto;
import com.sareemarket.follow.dto.FollowRequest;

import jakarta.validation.Valid;

/**
 * Follow endpoints for the logged-in reseller (a customer). Every endpoint is CUSTOMER only (class-level
 * @PreAuthorize). The rules are in FollowService; the notification feature reads the follows when it notifies.
 */
@RestController
@RequestMapping("/api/v1/follows")
@PreAuthorize("hasRole('CUSTOMER')")
class FollowController {

	private final FollowService service;

	FollowController(FollowService service) {
		this.service = service;
	}

	/**
	 * GET /api/v1/follows. The sellers this reseller follows, by shop name.
	 */
	@GetMapping
	List<FollowDto> list(@AuthenticationPrincipal Jwt jwt) {
		return service.list(Long.parseLong(jwt.getSubject()));
	}

	/**
	 * PUT /api/v1/follows/{sellerId}. Follows the seller, or changes the notify mode of an existing follow.
	 * The body is {"notifyMode": "INSTANT" | "MUTED"}; answers 204.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR when notifyMode is missing or not a known value;
	 * 	404 NOT_FOUND when the seller does not exist or is disabled
	 */
	@PutMapping("/{sellerId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void follow(@AuthenticationPrincipal Jwt jwt, @PathVariable long sellerId, @Valid @RequestBody FollowRequest request) {
		service.follow(Long.parseLong(jwt.getSubject()), sellerId, request.notifyMode());
	}

	/**
	 * DELETE /api/v1/follows/{sellerId}. Stops following; answers 204 even if the reseller did not follow.
	 */
	@DeleteMapping("/{sellerId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable long sellerId) {
		service.unfollow(Long.parseLong(jwt.getSubject()), sellerId);
	}

}
