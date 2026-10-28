package io.vanillabp.operaton.migration;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The workflow aggregate of every test in this module. The test sets the id, so it knows the
 * business key of each workflow before it starts.
 */
@Entity
@Table(name = "MIGRATION_AGGREGATE")
@Getter
@Setter
public class MigrationAggregate {

  @Id
  private String id;

  /** What the decision of the process reads. Above 100 it says "big". */
  @io.vanillabp.spi.service.SyncWithBPMS
  private int amount;

  /** Set by the first task of the process. */
  private boolean checked;

  /** Set by the last task of the process. */
  private boolean finished;

}
