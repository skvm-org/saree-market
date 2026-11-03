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

/**
 * Shops as resellers see them. Sellers have no business reading each other's contact details, so only the
 * roles CUSTOMER and ADMIN may call these endpoints; a seller gets 403.
 */
@RestController
@RequestMapping("/api/v1/sellers")
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
class SellerController {

	private final SellerService service;

	SellerController(SellerService service) {
		this.service = service;
	}

	/**
	 * GET /api/v1/sellers: every shop whose account is enabled, by shop name. Roles CUSTOMER and ADMIN.
	 */
	@GetMapping
	List<SellerListDto> list() {
		return service.list();
	}

	/**
	 * GET /api/v1/sellers/{id}: shop details with contact numbers, the viewer's follow state and the seller's
	 * collections. Roles CUSTOMER and ADMIN.
	 *
	 * @throws ApiException 404 NOT_FOUND when the seller does not exist or the account is disabled
	 */
	@GetMapping("/{id}")
	SellerDetailDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return service.get(id, Long.parseLong(jwt.getSubject()), "CUSTOMER".equals(jwt.getClaimAsString("role")));
	}

}
