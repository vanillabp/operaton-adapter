package io.vanillabp.operaton.it;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WiredByElementIdRepository extends JpaRepository<WiredByElementIdAggregate, String> {
}
