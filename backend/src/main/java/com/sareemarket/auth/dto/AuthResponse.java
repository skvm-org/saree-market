package com.sareemarket.auth.dto;

/**
 * Reply to register and login.
 *
 * @param token JWT to send as a Bearer token on later calls
 * @param user the account it belongs to
 */
public record AuthResponse(String token, UserDto user) {
}
