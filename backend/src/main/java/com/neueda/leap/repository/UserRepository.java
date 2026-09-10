package com.neueda.leap.repository;

import com.neueda.leap.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<Users, Integer> {
    Optional<Users> findByUsername(String username);

    boolean existsByUsername(String username);
}