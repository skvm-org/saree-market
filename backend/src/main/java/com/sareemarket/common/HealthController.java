package com.sareemarket.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
class HealthController {

	record HealthResponse(String status) {
	}

	@GetMapping
	HealthResponse health() {
		return new HealthResponse("UP");
	}

}
