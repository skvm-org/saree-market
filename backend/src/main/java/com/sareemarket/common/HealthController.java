package com.sareemarket.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness check for the load balancer and deploy scripts. Public, and left out of the request log. */
@RestController
@RequestMapping("/api/v1/health")
class HealthController {

	record HealthResponse(String status) {
	}

	/** GET /api/v1/health. Open to anyone. Always answers 200 with status UP while the app is running; it does not check the database. */
	@GetMapping
	HealthResponse health() {
		return new HealthResponse("UP");
	}

}
