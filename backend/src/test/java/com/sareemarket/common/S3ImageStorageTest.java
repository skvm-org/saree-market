package com.sareemarket.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ImageStorageTest {

	private final S3Client s3 = mock(S3Client.class);

	private final S3ImageStorage storage = new S3ImageStorage(s3, "saree-market", "saree-collections", "payment-proofs/");

	@Test
	void aPhotoIsSavedUnderThePhotoFolder() {
		storage.save("7/abc.jpg", new byte[] { 1, 2, 3 });

		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3).putObject(request.capture(), any(RequestBody.class));
		assertThat(request.getValue().bucket()).isEqualTo("saree-market");
		assertThat(request.getValue().key()).isEqualTo("saree-collections/7/abc.jpg"); // the missing slash is added
		assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
	}

	@Test
	void paymentProofsGoOutsideThePhotoFolder() {
		assertThat(storage.objectKey("proofs/12-9f.jpg")).isEqualTo("payment-proofs/12-9f.jpg");
		assertThat(storage.objectKey("7/abc_thumb.jpg")).isEqualTo("saree-collections/7/abc_thumb.jpg");
	}

	@Test
	void aPhotoIsReadBackFromTheBucket() throws Exception {
		when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
			.thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] { 9, 8, 7 }));

		Resource resource = storage.load("7/abc.jpg");

		assertThat(resource.exists()).isTrue();
		assertThat(resource.getContentAsByteArray()).containsExactly(9, 8, 7);
		ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
		verify(s3).getObjectAsBytes(request.capture());
		assertThat(request.getValue().key()).isEqualTo("saree-collections/7/abc.jpg");
	}

	@Test
	void aMissingPhotoIsAResourceThatDoesNotExist() {
		when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

		assertThat(storage.load("7/gone.jpg").exists()).isFalse();
	}

	@Test
	void deletingUsesTheSameKeyMapping() {
		storage.delete("proofs/12-9f.jpg");

		ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
		verify(s3).deleteObject(request.capture());
		assertThat(request.getValue().key()).isEqualTo("payment-proofs/12-9f.jpg");
	}

	@Test
	void keysThatClimbOutOfTheirFolderAreRefused() {
		assertThatThrownBy(() -> storage.save("../secret.jpg", new byte[0])).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.load("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.delete("7/..\\x.jpg")).isInstanceOf(IllegalArgumentException.class);
	}

}
