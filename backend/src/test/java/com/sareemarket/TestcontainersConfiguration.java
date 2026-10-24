package com.sareemarket;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.sareemarket.common.ImageStorage;
import com.sareemarket.common.LocalDiskImageStorage;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// Photos go to a throwaway folder instead of data/uploads.
	@Bean
	@Primary
	ImageStorage testImageStorage() throws java.io.IOException {
		return new LocalDiskImageStorage(java.nio.file.Files.createTempDirectory("saree-test-uploads"));
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

}
