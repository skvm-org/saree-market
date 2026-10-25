package com.sareemarket.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.sareemarket.TestImages;
import com.sareemarket.TestcontainersConfiguration;

/** MockMvc skips the servlet container, so the 10 MB limit is only checked against a real running server. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class UploadLimitRealServerTest {

	@LocalServerPort
	int port;


	private String sellerToken(HttpClient http) throws Exception {
		String phone = "7" + (long) (Math.random() * 900_000_000L + 100_000_000L);
		String body = """
				{"phone":"%s","name":"Lakshmi","password":"password1","role":"SELLER",
				 "shopName":"Silks","marketLocation":"Erode","contactPhone":"%s"}""".formatted(phone, phone);
		var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
			.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(201);
		return com.jayway.jsonpath.JsonPath.read(response.body(), "$.token");
	}

	private HttpResponse<String> upload(HttpClient http, String token, byte[] photo) throws Exception {
		String boundary = "----test" + System.nanoTime();
		var out = new ByteArrayOutputStream();
		String[][] fields = { { "title", "Big" }, { "sareeTypeId", "1" }, { "fabricId", "1" }, { "price", "1000" } };
		for (String[] f : fields) {
			out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f[0] + "\"\r\n\r\n" + f[1]
					+ "\r\n").getBytes(StandardCharsets.UTF_8));
		}
		out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"p.jpg\"\r\n"
				+ "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		out.write(photo);
		out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/collections"))
			.header("Authorization", "Bearer " + token)
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void aPhotoOverTenMegabytesIsRefusedWithThePhotoSizeMessage() throws Exception {
		HttpClient http = HttpClient.newHttpClient();
		byte[] big = new byte[11 * 1024 * 1024];
		byte[] jpegStart = TestImages.jpeg(64, 64, 1);
		System.arraycopy(jpegStart, 0, big, 0, jpegStart.length);

		HttpResponse<String> response = upload(http, sellerToken(http), big);

		assertThat(response.statusCode()).isEqualTo(413);
		assertThat(response.body()).contains("PAYLOAD_TOO_LARGE");
	}

	@Test
	void aNormalPhotoIsAcceptedByTheSameServer() throws Exception {
		HttpClient http = HttpClient.newHttpClient();

		HttpResponse<String> response = upload(http, sellerToken(http), TestImages.jpeg(600, 800, 3));

		assertThat(response.statusCode()).isEqualTo(201);
	}

}
