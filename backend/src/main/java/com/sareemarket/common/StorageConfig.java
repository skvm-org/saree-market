package com.sareemarket.common;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** {@code app.storage.type}: {@code local} (default, the folder in {@code app.storage.root}) or {@code s3}. */
@Configuration
class StorageConfig {

	/** Local-disk storage, used when app.storage.type is {@code local} or not set. */
	@Bean
	@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
	// A String, not a Path: Spring converts a Path value as a servlet resource, which rejects "../data/uploads".
	ImageStorage imageStorage(@Value("${app.storage.root}") String root) {
		return new LocalDiskImageStorage(Path.of(root));
	}

	/** The S3 client, created only when app.storage.type is {@code s3}. */
	@Bean
	@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
	// Credentials come from the default chain: the EC2 instance role in production, never from the repo.
	S3Client s3Client(@Value("${app.storage.s3.region}") String region) {
		return S3Client.builder().region(Region.of(region)).httpClient(UrlConnectionHttpClient.create()).build();
	}

	/**
	 * S3 storage, wrapped in {@link FallbackImageStorage} over the local folder when fallback-to-local is true so earlier
	 * uploads still show while they are copied to S3.
	 *
	 * @throws IllegalArgumentException at startup if no bucket is configured
	 */
	@Bean
	@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
	ImageStorage s3ImageStorage(S3Client s3, @Value("${app.storage.s3.bucket}") String bucket,
			@Value("${app.storage.s3.prefix}") String prefix, @Value("${app.storage.s3.proofs-prefix}") String proofsPrefix,
			@Value("${app.storage.s3.fallback-to-local}") boolean fallbackToLocal, @Value("${app.storage.root}") String root) {
		Assert.hasText(bucket, "app.storage.s3.bucket is required when app.storage.type is s3");
		ImageStorage s3Storage = new S3ImageStorage(s3, bucket, prefix, proofsPrefix);
		return fallbackToLocal ? new FallbackImageStorage(s3Storage, new LocalDiskImageStorage(Path.of(root))) : s3Storage;
	}

}
