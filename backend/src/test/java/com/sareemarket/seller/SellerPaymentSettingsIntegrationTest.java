package com.sareemarket.seller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.sareemarket.IntegrationTestBase;

class SellerPaymentSettingsIntegrationTest extends IntegrationTestBase {

	static String profile(String extra) {
		return "{\"shopName\":\"Lakshmi Silks\",\"marketLocation\":\"Erode\",\"contactPhone\":\"9111111111\"" + extra + "}";
	}

	@Test
	void newSellerHasNoUpiAndZeroShipping() throws Exception {
		getWith("/api/v1/seller/profile", registerSeller()).andExpect(status().isOk())
			.andExpect(jsonPath("$.upiId").doesNotExist())
			.andExpect(jsonPath("$.shippingFee").value(0));
	}

	@Test
	void sellerSavesUpiAndShippingFee() throws Exception {
		String seller = registerSeller();
		putJson("/api/v1/seller/profile", seller, profile(",\"upiId\":\"lakshmi.silks@okaxis\",\"shippingFee\":60"))
			.andExpect(status().isOk());
		getWith("/api/v1/seller/profile", seller).andExpect(jsonPath("$.upiId").value("lakshmi.silks@okaxis"))
			.andExpect(jsonPath("$.shippingFee").value(60));
	}

	@Test
	void omittingUpiAndFeeLeavesThemAsTheyWere() throws Exception {
		String seller = registerSeller();
		putJson("/api/v1/seller/profile", seller, profile(",\"upiId\":\"shop@ybl\",\"shippingFee\":40"))
			.andExpect(status().isOk());
		putJson("/api/v1/seller/profile", seller, profile("")).andExpect(status().isOk())
			.andExpect(jsonPath("$.upiId").value("shop@ybl"))
			.andExpect(jsonPath("$.shippingFee").value(40));
	}

	@Test
	void blankUpiClearsIt() throws Exception {
		String seller = registerSeller();
		putJson("/api/v1/seller/profile", seller, profile(",\"upiId\":\"shop@ybl\"")).andExpect(status().isOk());
		putJson("/api/v1/seller/profile", seller, profile(",\"upiId\":\"\"")).andExpect(status().isOk())
			.andExpect(jsonPath("$.upiId").doesNotExist());
	}

	@Test
	void malformedUpiIsRejected() throws Exception {
		String seller = registerSeller();
		for (String bad : new String[] { "abc", "a@b", "x y@okaxis" }) {
			putJson("/api/v1/seller/profile", seller, profile(",\"upiId\":\"" + bad + "\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.upiId").exists());
		}
	}

	@Test
	void negativeOrHugeShippingFeeIsRejected() throws Exception {
		String seller = registerSeller();
		for (String bad : new String[] { "-1", "10001" }) {
			putJson("/api/v1/seller/profile", seller, profile(",\"shippingFee\":" + bad))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.shippingFee").exists());
		}
	}

	@Test
	void customerCannotUpdateSellerProfile() throws Exception {
		putJson("/api/v1/seller/profile", registerCustomer(), profile(",\"upiId\":\"shop@ybl\""))
			.andExpect(status().isForbidden());
	}

}
