package com.sareemarket.reference;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FabricRepository extends JpaRepository<Fabric, Long> {

	List<Fabric> findByActiveTrueOrderByName();

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

}
