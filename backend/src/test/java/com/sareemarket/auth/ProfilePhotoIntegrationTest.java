package com.sareemarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.sareemarket.IntegrationTestBase;
import com.sareemarket.TestImages;
import com.sareemarket.common.ImageStorage;

/** Setting, replacing and removing one's own profile picture. Who may see it is in ProfilePhotoVisibilityIntegrationTest. */
class ProfilePhotoIntegrationTest extends IntegrationTestBase {

	@Autowired
	ImageStorage storage;

	ResultActions upload(String token, byte[] bytes, String filename) throws Exception {
		return mvc.perform(multipart(HttpMethod.PUT, "/api/v1/auth/me/photo")
			.file(new MockMultipartFile("photo", filename, "image/jpeg", bytes))
			.header("Authorization", "Bearer " + token));
	}

	String keyOf(String token) {
		return jdbc.queryForObject("SELECT photo_key FROM app_user WHERE id = ?", String.class, userId(token));
	}

	BufferedImage stored(String key) throws IOException {
		try (InputStream in = storage.load(key).getInputStream()) {
			return ImageIO.read(in);
		}
	}

	@Test
	void uploadStoresASquareJpegAndReturnsAPhotoVersion() throws Exception {
		String token = registerCustomer();
		getWith("/api/v1/auth/me", token).andExpect(jsonPath("$.photoVersion").doesNotExist());

		upload(token, TestImages.jpeg(1000, 600, 1), "me.jpg").andExpect(status().isOk())
			.andExpect(jsonPath("$.photoVersion").isNumber())
			.andExpect(jsonPath("$.name").value("Priya"));

		String key = keyOf(token);
		assertThat(key).startsWith("avatars/" + userId(token) + "-").endsWith(".jpg");
		BufferedImage image = stored(key);
		assertThat(image.getWidth()).isEqualTo(512);
		assertThat(image.getHeight()).isEqualTo(512);
		getWith("/api/v1/auth/me", token).andExpect(jsonPath("$.photoVersion").isNumber());
	}

	@Test
	void aSmallPictureIsCroppedToASquareButNotEnlarged() throws Exception {
		String token = registerSeller();

		upload(token, TestImages.jpeg(200, 320, 2), "small.jpg").andExpect(status().isOk());

		BufferedImage image = stored(keyOf(token));
		assertThat(image.getWidth()).isEqualTo(200);
		assertThat(image.getHeight()).isEqualTo(200);
	}

	@Test
	void replacingDeletesTheOldFileAndChangesTheVersion() throws Exception {
		String token = registerCustomer();
		upload(token, TestImages.jpeg(600, 600, 3), "a.jpg").andExpect(status().isOk());
		String first = keyOf(token);
		Long firstVersion = jdbc.queryForObject("SELECT (extract(epoch FROM photo_updated_at) * 1000)::bigint FROM app_user WHERE id = ?",
				Long.class, userId(token));
		assertThat(storage.load(first).exists()).isTrue();

		upload(token, TestImages.jpeg(600, 600, 4), "b.jpg").andExpect(status().isOk());

		String second = keyOf(token);
		assertThat(second).isNotEqualTo(first);
		assertThat(storage.load(second).exists()).isTrue();
		assertThat(storage.load(first).exists()).isFalse();
		Long secondVersion = jdbc.queryForObject("SELECT (extract(epoch FROM photo_updated_at) * 1000)::bigint FROM app_user WHERE id = ?",
				Long.class, userId(token));
		assertThat(secondVersion).isGreaterThanOrEqualTo(firstVersion);
	}

	@Test
	void removingClearsThePictureAndDeletesTheFile() throws Exception {
		String token = registerSeller();
		upload(token, TestImages.jpeg(600, 600, 5), "a.jpg").andExpect(status().isOk());
		String key = keyOf(token);

		deleteWith("/api/v1/auth/me/photo", token).andExpect(status().isNoContent());

		assertThat(keyOf(token)).isNull();
		assertThat(storage.load(key).exists()).isFalse();
		getWith("/api/v1/auth/me", token).andExpect(jsonPath("$.photoVersion").doesNotExist());
		getWith("/api/v1/users/" + userId(token) + "/photo", token).andExpect(status().isNotFound());
	}

	@Test
	void removingWhenThereIsNoPictureIsStillFine() throws Exception {
		String token = registerCustomer();

		deleteWith("/api/v1/auth/me/photo", token).andExpect(status().isNoContent());
		deleteWith("/api/v1/auth/me/photo", token).andExpect(status().isNoContent());
	}

	@Test
	void aFileThatIsNotAPictureIsRefusedAndNothingChanges() throws Exception {
		String token = registerCustomer();
		upload(token, TestImages.jpeg(600, 600, 6), "a.jpg").andExpect(status().isOk());
		String before = keyOf(token);

		upload(token, "this is not an image".getBytes(), "notes.jpg").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

		assertThat(keyOf(token)).isEqualTo(before);
		assertThat(storage.load(before).exists()).isTrue();
	}

	@Test
	void aMissingOrEmptyFileIsAValidationError() throws Exception {
		String token = registerCustomer();

		mvc.perform(multipart(HttpMethod.PUT, "/api/v1/auth/me/photo").header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		upload(token, new byte[0], "empty.jpg").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void anonymousUsersCannotUploadOrRemove() throws Exception {
		mvc.perform(multipart(HttpMethod.PUT, "/api/v1/auth/me/photo")
			.file(new MockMultipartFile("photo", "a.jpg", "image/jpeg", TestImages.jpeg(100, 100, 7))))
			.andExpect(status().isUnauthorized());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/auth/me/photo"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void theOwnerCanViewItAndTheVersionDecidesHowLongItIsCached() throws Exception {
		String token = registerCustomer();
		upload(token, TestImages.jpeg(600, 600, 8), "a.jpg").andExpect(status().isOk());
		String path = "/api/v1/users/" + userId(token) + "/photo";

		getWith(path + "?v=123", token).andExpect(status().isOk())
			.andExpect(header().string("Content-Type", containsString("image/jpeg")))
			.andExpect(header().string("Cache-Control", containsString("immutable")));
		getWith(path, token).andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", containsString("no-cache")));
	}

	@Test
	void uploadWorksForEveryRole() throws Exception {
		upload(registerSeller(), TestImages.jpeg(300, 300, 9), "s.jpg").andExpect(status().isOk());
		upload(registerCustomer(), TestImages.jpeg(300, 300, 10), "c.jpg").andExpect(status().isOk());
		upload(adminToken(), TestImages.jpeg(300, 300, 11), "a.jpg").andExpect(status().isOk());
	}

}
