package io.vanillabp.operaton.it;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionedTestRepository extends JpaRepository<VersionedTestAggregate, Long> {

}
