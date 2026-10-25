package com.sareemarket.reference;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SareeTypeRepository extends JpaRepository<SareeType, Long> {

	List<SareeType> findByActiveTrueOrderByName();

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

}
