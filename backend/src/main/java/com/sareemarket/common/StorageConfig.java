package com.sareemarket.common;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class StorageConfig {

	@Bean
	// A String, not a Path: Spring converts a Path value as a servlet resource, which rejects "../data/uploads".
	ImageStorage imageStorage(@Value("${app.storage.root}") String root) {
		return new LocalDiskImageStorage(Path.of(root));
	}

}
