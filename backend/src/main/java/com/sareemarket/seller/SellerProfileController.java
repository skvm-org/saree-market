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

/** A seller's own shop details. */
@RestController
@RequestMapping("/api/v1/seller/profile")
@PreAuthorize("hasRole('SELLER')")
class SellerProfileController {

	private final SellerService service;

	SellerProfileController(SellerService service) {
		this.service = service;
	}

	@GetMapping
	SellerProfileDto get(@AuthenticationPrincipal Jwt jwt) {
		return service.mine(Long.parseLong(jwt.getSubject()));
	}

	@PutMapping
	SellerProfileDto update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SellerProfileRequest request) {
		return service.updateMine(Long.parseLong(jwt.getSubject()), request);
	}

}
