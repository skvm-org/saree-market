package com.sareemarket.seller;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "seller_profile")
public class SellerProfile {

	@Id
	private Long userId;

	private String shopName;

	private String marketLocation;

	private String contactPhone;

	private String whatsappNumber;

	private String upiId;

	private int shippingFee;

	protected SellerProfile() {
	}

	public SellerProfile(Long userId, String shopName, String marketLocation, String contactPhone,
			String whatsappNumber) {
		this.userId = userId;
		this.shopName = shopName;
		this.marketLocation = marketLocation;
		this.contactPhone = contactPhone;
		this.whatsappNumber = whatsappNumber;
	}

	public Long getUserId() {
		return userId;
	}

	public String getShopName() {
		return shopName;
	}

	public String getMarketLocation() {
		return marketLocation;
	}

	public String getContactPhone() {
		return contactPhone;
	}

	public String getWhatsappNumber() {
		return whatsappNumber;
	}

	public String getUpiId() {
		return upiId;
	}

	public int getShippingFee() {
		return shippingFee;
	}

	public void updatePayment(String upiId, int shippingFee) {
		this.upiId = upiId;
		this.shippingFee = shippingFee;
	}

	public void update(String shopName, String marketLocation, String contactPhone, String whatsappNumber) {
		this.shopName = shopName;
		this.marketLocation = marketLocation;
		this.contactPhone = contactPhone;
		this.whatsappNumber = whatsappNumber;
	}

}
