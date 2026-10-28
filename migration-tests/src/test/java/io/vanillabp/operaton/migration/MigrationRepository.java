package io.vanillabp.operaton.migration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationRepository extends JpaRepository<MigrationAggregate, String> {

}
