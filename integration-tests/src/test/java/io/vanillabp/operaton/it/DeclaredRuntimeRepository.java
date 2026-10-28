package io.vanillabp.operaton.it;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeclaredRuntimeRepository extends JpaRepository<DeclaredRuntimeAggregate, String> {
}
