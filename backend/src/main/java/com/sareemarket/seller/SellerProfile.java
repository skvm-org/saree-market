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

	public void update(String shopName, String marketLocation, String contactPhone, String whatsappNumber) {
		this.shopName = shopName;
		this.marketLocation = marketLocation;
		this.contactPhone = contactPhone;
		this.whatsappNumber = whatsappNumber;
	}

}
