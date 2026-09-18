package com.neueda.leap.config;

import com.neueda.leap.repository.query.OrderHistoryRepository;
import com.neueda.leap.repository.query.CashTransactionHistoryRepository;
import com.neueda.leap.repository.query.PriceHistoryRepository;
import com.neueda.leap.repository.query.PortfolioHistoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import jakarta.persistence.EntityManager;

/**
 * Configuration for query-only repositories that don't manage JPA entities.
 * These repositories only use @Query methods to return DTOs.
 * Manually creates beans from repositories in com.neueda.leap.repository.query package.
 */
@Configuration
public class RepositoryConfig {

    private final EntityManager entityManager;

    public RepositoryConfig(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Bean
    public OrderHistoryRepository orderHistoryRepository() {
        JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
        return factory.getRepository(OrderHistoryRepository.class);
    }

    @Bean
    public CashTransactionHistoryRepository cashTransactionHistoryRepository() {
        JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
        return factory.getRepository(CashTransactionHistoryRepository.class);
    }

    @Bean
    public PriceHistoryRepository priceHistoryRepository() {
        JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
        return factory.getRepository(PriceHistoryRepository.class);
    }

    @Bean
    public PortfolioHistoryRepository portfolioHistoryRepository() {
        JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
        return factory.getRepository(PortfolioHistoryRepository.class);
    }
}
