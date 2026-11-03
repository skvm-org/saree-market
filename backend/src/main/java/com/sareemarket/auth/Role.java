package com.sareemarket.auth;

/**
 * The three kinds of account. SELLER and CUSTOMER are chosen at registration; ADMIN cannot be self-registered. The role
 * is copied into the JWT as the role claim, which Spring Security maps to ROLE_SELLER, ROLE_CUSTOMER or ROLE_ADMIN.
 */
public enum Role {
	SELLER, CUSTOMER, ADMIN
}
