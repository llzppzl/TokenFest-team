package com.tokenfest.gateway.repository;

import java.util.Optional;

import com.tokenfest.gateway.domain.AdminUser;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

	Optional<AdminUser> findByUsername(String username);

}
