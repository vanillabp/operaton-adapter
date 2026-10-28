package io.vanillabp.operaton.it;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ForeignPlainRepository extends JpaRepository<ForeignPlainAggregate, String> {
}
