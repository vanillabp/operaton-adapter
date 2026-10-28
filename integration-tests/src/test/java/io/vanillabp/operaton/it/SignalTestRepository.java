package io.vanillabp.operaton.it;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalTestRepository extends JpaRepository<SignalTestAggregate, Long> {

}
