package com.saryom.jobservice.rls;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class RlsConfig {

    /** Replaces Spring Boot's JpaTransactionManager; see {@link RlsTransactionManager}. */
    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory,
                                                  @Value("${saryom.jobs.rls.enabled:true}") boolean enabled) {
        return new RlsTransactionManager(entityManagerFactory, RequestIdentity::current, enabled);
    }
}
