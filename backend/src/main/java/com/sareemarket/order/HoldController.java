package com.sareemarket.order;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.order.dto.CheckoutRequest;
import com.sareemarket.order.dto.CheckoutResultDto;
import com.sareemarket.order.dto.HoldDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('CUSTOMER')")
class HoldController {

	private final HoldService holds;

	HoldController(HoldService holds) {
		this.holds = holds;
	}

	@PostMapping("/cart/checkout")
	CheckoutResultDto checkout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckoutRequest request) {
		return holds.checkout(Long.parseLong(jwt.getSubject()), request);
	}

	@GetMapping("/holds")
	List<HoldDto> list(@AuthenticationPrincipal Jwt jwt) {
		return holds.list(Long.parseLong(jwt.getSubject()));
	}

}
