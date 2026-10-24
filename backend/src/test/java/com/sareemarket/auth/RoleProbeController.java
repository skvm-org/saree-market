package com.sareemarket.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that exercise the role checks real features will use. */
@RestController
@RequestMapping("/api/v1/test")
class RoleProbeController {

	@GetMapping("/seller-only")
	@PreAuthorize("hasRole('SELLER')")
	String sellerOnly() {
		return "ok";
	}

	@GetMapping("/reseller-only")
	@PreAuthorize("hasRole('RESELLER')")
	String resellerOnly() {
		return "ok";
	}

	@GetMapping("/anyone-logged-in")
	String anyoneLoggedIn() {
		return "ok";
	}

}
