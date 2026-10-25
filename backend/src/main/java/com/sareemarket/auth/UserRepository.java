package com.sareemarket.auth;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByPhone(String phone);

	boolean existsByPhone(String phone);

	/** {@code q} matches part of the phone or name; empty matches everyone. {@code role} null matches every role. */
	@Query("""
			SELECT u FROM AppUser u
			WHERE (:q = '' OR u.phone LIKE CONCAT('%', :q, '%') OR LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')))
			  AND (:role IS NULL OR u.role = :role)
			""")
	Page<AppUser> search(@Param("q") String q, @Param("role") Role role, Pageable pageable);

}
