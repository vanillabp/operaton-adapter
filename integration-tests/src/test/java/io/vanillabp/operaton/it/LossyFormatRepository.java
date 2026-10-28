package io.vanillabp.operaton.it;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LossyFormatRepository extends JpaRepository<LossyFormatAggregate, Long> {
}
