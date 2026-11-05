package com.sareemarket.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Account row in table app_user, for every role (customer, seller, admin). Phone is the login name. Written by
 * AuthService (register, name, password) and UserAdminService (enabled); read by the security layer on every request.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 10-digit mobile number, unique; used as the login name. */
	private String phone;

	/** BCrypt hash; the plain password is never stored. */
	private String passwordHash;

	private String name;

	@Enumerated(EnumType.STRING)
	private Role role;

	/** False once an admin disables the account; a disabled user cannot log in and existing tokens stop working. */
	private boolean enabled = true;

	/** Storage key of the profile picture (for example avatars/7-uuid.jpg), or null when there is none. */
	private String photoKey;

	/** When the picture last changed; null when there is none. Clients use it as the picture's cache-busting version. */
	private Instant photoUpdatedAt;

	/** Set by the database default on insert; never written from Java. */
	@Column(insertable = false, updatable = false)
	private Instant createdAt;

	protected AppUser() {
	}

	public AppUser(String phone, String passwordHash, String name, Role role) {
		this.phone = phone;
		this.passwordHash = passwordHash;
		this.name = name;
		this.role = role;
	}

	public Long getId() {
		return id;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getName() {
		return name;
	}

	public Role getRole() {
		return role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getPhotoKey() {
		return photoKey;
	}

	public Instant getPhotoUpdatedAt() {
		return photoUpdatedAt;
	}

	/** When the picture last changed in epoch milliseconds (the client's cache-busting version), or null when there is none. */
	public Long photoVersion() {
		return photoKey == null || photoUpdatedAt == null ? null : photoUpdatedAt.toEpochMilli();
	}

	/** Points the account at a new picture (or null for none) and stamps the change time. */
	public void setPhoto(String photoKey, Instant at) {
		this.photoKey = photoKey;
		this.photoUpdatedAt = photoKey == null ? null : at;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

}
