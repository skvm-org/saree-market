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

	public void setName(String name) {
		this.name = name;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

}
