package com.sareemarket.common;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class StorageConfig {

	@Bean
	ImageStorage imageStorage(@Value("${app.storage.root}") Path root) {
		return new LocalDiskImageStorage(root);
	}

}
