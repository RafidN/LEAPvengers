package com.neueda.leap.repository;

import com.neueda.leap.model.Clients;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Clients, Integer> {
}