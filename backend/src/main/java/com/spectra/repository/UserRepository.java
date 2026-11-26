package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.spectra.entity.User;

public interface UserRepository extends JpaRepository<User, Long>{
	Optional<User> findByEmail(String email);
	@Query("SELECT u FROM User u LEFT JOIN FETCH u.enterprise WHERE u.email = :email")
	Optional<User> findByEmailWithEnterprise(@Param("email") String email);
	boolean existsByEmail(String email);
	List<User> findByEnterpriseId(Long enterpriseId);
	long countByEnterpriseId(Long enterpriseId);
}
