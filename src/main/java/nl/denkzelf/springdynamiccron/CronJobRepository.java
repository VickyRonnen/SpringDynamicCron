package nl.denkzelf.springdynamiccron;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface CronJobRepository extends JpaRepository<CronJob, String> {
    long countByActiveTrue();

    List<CronJob> findByActiveTrue();

    List<CronJob> findByActiveFalse();
}
