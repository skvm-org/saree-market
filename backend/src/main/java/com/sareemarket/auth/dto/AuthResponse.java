package com.sareemarket.auth.dto;

public record AuthResponse(String token, UserDto user) {
}
