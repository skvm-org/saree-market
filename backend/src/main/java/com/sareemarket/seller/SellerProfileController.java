package com.sareemarket.seller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.seller.dto.SellerProfileDto;
import com.sareemarket.seller.dto.SellerProfileRequest;

import jakarta.validation.Valid;

/** A seller's own shop details. Role SELLER only; the seller is always the user in the JWT. */
@RestController
@RequestMapping("/api/v1/seller/profile")
@PreAuthorize("hasRole('SELLER')")
class SellerProfileController {

	private final SellerService service;

	SellerProfileController(SellerService service) {
		this.service = service;
	}

	/**
	 * GET /api/v1/seller/profile: the caller's own shop details, including UPI ID and shipping fee.
	 * Returns 404 NOT_FOUND when the caller has no profile.
	 */
	@GetMapping
	SellerProfileDto get(@AuthenticationPrincipal Jwt jwt) {
		return service.mine(Long.parseLong(jwt.getSubject()));
	}

	/**
	 * PUT /api/v1/seller/profile: updates the caller's shop details and payment settings.
	 * Returns 400 VALIDATION_ERROR (with fields) for a bad request body, and 404 NOT_FOUND when there is no profile.
	 */
	@PutMapping
	SellerProfileDto update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SellerProfileRequest request) {
		return service.updateMine(Long.parseLong(jwt.getSubject()), request);
	}

}
