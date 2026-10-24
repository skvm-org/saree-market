package com.sareemarket.seller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.seller.dto.SellerDetailDto;
import com.sareemarket.seller.dto.SellerListDto;

/** Shops as resellers see them. Sellers have no business reading each other's contact details. */
@RestController
@RequestMapping("/api/v1/sellers")
@PreAuthorize("hasAnyRole('RESELLER', 'ADMIN')")
class SellerController {

	private final SellerService service;

	SellerController(SellerService service) {
		this.service = service;
	}

	@GetMapping
	List<SellerListDto> list() {
		return service.list();
	}

	@GetMapping("/{id}")
	SellerDetailDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return service.get(id, Long.parseLong(jwt.getSubject()), "RESELLER".equals(jwt.getClaimAsString("role")));
	}

}
