package com.sareemarket.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test only: an endpoint that fails the way a bug would, so the 500 path can be checked. Behind login like the rest. */
@RestController
class LoggingTestController {

	@GetMapping("/api/v1/test/boom")
	String boom() {
		throw new IllegalStateException("kaboom with internal detail");
	}

}
