package com.neueda.leap.repository;

import com.neueda.leap.model.Accounts;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Accounts, Integer> {
    boolean existsByAccountIdAndClientId(Integer accountId, Integer clientId);
}
