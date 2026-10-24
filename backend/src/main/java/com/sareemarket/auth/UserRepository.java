package com.sareemarket.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByPhone(String phone);

	boolean existsByPhone(String phone);

}
