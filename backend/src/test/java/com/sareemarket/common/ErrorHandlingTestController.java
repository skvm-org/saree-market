package com.sareemarket.common;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test only: endpoints that fail the way the database can, so each mapped error response can be checked. */
@RestController
class ErrorHandlingTestController {

	@GetMapping("/api/v1/test/constraint")
	String constraint() {
		throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"secret_idx\"");
	}

	@GetMapping("/api/v1/test/lock")
	String lock() {
		throw new CannotAcquireLockException("could not obtain lock on row");
	}

	@GetMapping("/api/v1/test/db-down")
	String dbDown() {
		throw new DataAccessResourceFailureException("connection refused to 10.0.0.5");
	}

}
