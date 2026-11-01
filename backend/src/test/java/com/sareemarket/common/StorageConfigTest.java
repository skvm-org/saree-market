package com.sareemarket.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Which ImageStorage the app builds from app.storage.* (no AWS call is made: the S3 client is only built). */
class StorageConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(StorageConfig.class);

	private String root() throws Exception {
		return Files.createTempDirectory("saree-config").toString();
	}

	@Test
	void localDiskIsTheDefault() throws Exception {
		runner.withPropertyValues("app.storage.root=" + root())
			.run(context -> assertThat(context.getBean(ImageStorage.class)).isInstanceOf(LocalDiskImageStorage.class));
	}

	@Test
	void s3WithFallbackReadsOldUploadsFromDisk() throws Exception {
		runner.withPropertyValues("app.storage.type=s3", "app.storage.root=" + root(), "app.storage.s3.bucket=saree-market",
				"app.storage.s3.prefix=saree-collections/", "app.storage.s3.proofs-prefix=payment-proofs/",
				"app.storage.s3.region=us-east-1", "app.storage.s3.fallback-to-local=true")
			.run(context -> assertThat(context.getBean(ImageStorage.class)).isInstanceOf(FallbackImageStorage.class));
	}

	@Test
	void s3WithoutFallbackIsPlainS3() throws Exception {
		runner.withPropertyValues("app.storage.type=s3", "app.storage.root=" + root(), "app.storage.s3.bucket=saree-market",
				"app.storage.s3.prefix=saree-collections/", "app.storage.s3.proofs-prefix=payment-proofs/",
				"app.storage.s3.region=us-east-1", "app.storage.s3.fallback-to-local=false")
			.run(context -> assertThat(context.getBean(ImageStorage.class)).isInstanceOf(S3ImageStorage.class));
	}

	@Test
	void s3WithoutABucketFailsAtStartup() throws Exception {
		runner.withPropertyValues("app.storage.type=s3", "app.storage.root=" + root(), "app.storage.s3.bucket=",
				"app.storage.s3.prefix=saree-collections/", "app.storage.s3.proofs-prefix=payment-proofs/",
				"app.storage.s3.region=us-east-1", "app.storage.s3.fallback-to-local=true")
			.run(context -> assertThat(context).hasFailed());
	}

}
