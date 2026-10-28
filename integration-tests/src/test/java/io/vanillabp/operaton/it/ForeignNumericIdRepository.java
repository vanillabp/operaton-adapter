package io.vanillabp.operaton.it;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ForeignNumericIdRepository extends JpaRepository<ForeignNumericIdAggregate, Long> {

}
