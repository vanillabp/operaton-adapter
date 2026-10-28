package io.vanillabp.operaton.it;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTestRepository extends JpaRepository<TaskTestAggregate, Long> {
}
