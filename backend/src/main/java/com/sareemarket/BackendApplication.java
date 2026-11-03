package com.sareemarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Saree Market backend. Starts Spring Boot with component scanning over every feature
 * package under com.sareemarket (auth, seller, collection, item, catalog, order and so on).
 */
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
