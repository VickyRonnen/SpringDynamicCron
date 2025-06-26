package nl.denkzelf.springdynamiccron;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CronJobRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CronJobRepository cronJobRepository;

    private CronJob activeJob1;
    private CronJob activeJob2;
    private CronJob inactiveJob1;
    private CronJob inactiveJob2;

    @BeforeEach
    void setUp() {
        cronJobRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        LocalDateTime now = LocalDateTime.now();

        activeJob1 = CronJob.builder()
                .name("active-job-1")
                .cronExpression("0 */5 * * * *")
                .description("Active job 1 - every 5 minutes")
                .jobClass("com.example.ActiveJob1")
                .active(true)
                .createdAt(now.minusDays(1))
                .build();

        activeJob2 = CronJob.builder()
                .name("active-job-2")
                .cronExpression("0 0 * * * *")
                .description("Active job 2 - every hour")
                .jobClass("com.example.ActiveJob2")
                .active(true)
                .createdAt(now.minusHours(2))
                .build();

        inactiveJob1 = CronJob.builder()
                .name("inactive-job-1")
                .cronExpression("0 0 0 * * *")
                .description("Inactive job 1 - daily")
                .jobClass("com.example.InactiveJob1")
                .active(false)
                .createdAt(now.minusHours(1))
                .build();

        inactiveJob2 = CronJob.builder()
                .name("inactive-job-2")
                .cronExpression("0 0 0 * * SUN")
                .description("Inactive job 2 - weekly")
                .jobClass("com.example.InactiveJob2")
                .active(false)
                .createdAt(now)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        // When
        CronJob saved = cronJobRepository.save(activeJob1);
        entityManager.flush();

        // Then
        Optional<CronJob> found = cronJobRepository.findById("active-job-1");

        assertThat(found).isPresent();
        assertThat(saved).isEqualTo(found.get());
        assertThat(found.get().getName()).isEqualTo("active-job-1");
        assertThat(found.get().getCronExpression()).isEqualTo("0 */5 * * * *");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    void testFindById_NotFound() {
        // When
        Optional<CronJob> found = cronJobRepository.findById("non-existent-job");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void testCountByActiveTrue_WithNoJobs() {
        // When
        long count = cronJobRepository.countByActiveTrue();

        // Then
        assertThat(count).isZero();
    }

    @Test
    void testCountByActiveTrue_WithMixedJobs() {
        // Given
        cronJobRepository.save(activeJob1);
        cronJobRepository.save(activeJob2);
        cronJobRepository.save(inactiveJob1);
        cronJobRepository.save(inactiveJob2);
        entityManager.flush();

        // When
        long count = cronJobRepository.countByActiveTrue();

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testCountByActiveTrue_WithOnlyActiveJobs() {
        // Given
        cronJobRepository.save(activeJob1);
        cronJobRepository.save(activeJob2);
        entityManager.flush();

        // When
        long count = cronJobRepository.countByActiveTrue();

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testCountByActiveTrue_WithOnlyInactiveJobs() {
        // Given
        cronJobRepository.save(inactiveJob1);
        cronJobRepository.save(inactiveJob2);
        entityManager.flush();

        // When
        long count = cronJobRepository.countByActiveTrue();

        // Then
        assertThat(count).isZero();
    }

    @Test
    void testFindByActiveTrue_WithNoJobs() {
        // When
        List<CronJob> activeJobs = cronJobRepository.findByActiveTrue();

        // Then
        assertThat(activeJobs).isEmpty();
    }

    @Test
    void testFindByActiveTrue_WithMixedJobs() {
        // Given
        cronJobRepository.save(activeJob1);
        cronJobRepository.save(activeJob2);
        cronJobRepository.save(inactiveJob1);
        cronJobRepository.save(inactiveJob2);
        entityManager.flush();

        // When
        List<CronJob> activeJobs = cronJobRepository.findByActiveTrue();

        // Then
        assertThat(activeJobs).hasSize(2);
        assertThat(activeJobs).extracting(CronJob::getName)
                .containsExactlyInAnyOrder("active-job-1", "active-job-2");
        assertThat(activeJobs).allMatch(CronJob::isActive);
    }

    @Test
    void testFindByActiveTrue_WithOnlyActiveJobs() {
        // Given
        cronJobRepository.save(activeJob1);
        cronJobRepository.save(activeJob2);
        entityManager.flush();

        // When
        List<CronJob> activeJobs = cronJobRepository.findByActiveTrue();

        // Then
        assertThat(activeJobs).hasSize(2).allMatch(CronJob::isActive);
    }

    @Test
    void testFindByActiveTrue_WithOnlyInactiveJobs() {
        // Given
        cronJobRepository.save(inactiveJob1);
        cronJobRepository.save(inactiveJob2);
        entityManager.flush();

        // When
        List<CronJob> activeJobs = cronJobRepository.findByActiveTrue();

        // Then
        assertThat(activeJobs).isEmpty();
    }

    @Test
    void testFindByActiveFalse_WithNoJobs() {
        // When
        List<CronJob> inactiveJobs = cronJobRepository.findByActiveFalse();

        // Then
        assertThat(inactiveJobs).isEmpty();
    }

    @Test
    void testFindByActiveFalse_WithMixedJobs() {
        // Given
        cronJobRepository.save(activeJob1);
        cronJobRepository.save(activeJob2);
        cronJobRepository.save(inactiveJob1);
        cronJobRepository.save(inactiveJob2);
        entityManager.flush();

        // When
        List<CronJob> inactiveJobs = cronJobRepository.findByActiveFalse();

        // Then
        assertThat(inactiveJobs).hasSize(2);
        assertThat(inactiveJobs).extracting(CronJob::getName)
                .containsExactlyInAnyOrder("inactive-job-1", "inactive-job-2");
        assertThat(inactiveJobs).allMatch(job -> !job.isActive());
    }

    @Test
    void testFindByActiveFalse_WithOnlyActiveJobs() {
        // Given
        cronJobRepository.save(activeJob1);
        cronJobRepository.save(activeJob2);
        entityManager.flush();

        // When
        List<CronJob> inactiveJobs = cronJobRepository.findByActiveFalse();

        // Then
        assertThat(inactiveJobs).isEmpty();
    }

    @Test
    void testFindByActiveFalse_WithOnlyInactiveJobs() {
        // Given
        cronJobRepository.save(inactiveJob1);
        cronJobRepository.save(inactiveJob2);
        entityManager.flush();

        // When
        List<CronJob> inactiveJobs = cronJobRepository.findByActiveFalse();

        // Then
        assertThat(inactiveJobs).hasSize(2).allMatch(job -> !job.isActive());
    }

    @Test
    void testUpdate_ChangeActiveStatus() {
        // Given
        CronJob saved = cronJobRepository.save(activeJob1);
        entityManager.flush();
        entityManager.clear();

        // When - Change active status
        saved.setActive(false);
        cronJobRepository.save(saved);
        entityManager.flush();

        // Then
        Optional<CronJob> found = cronJobRepository.findById("active-job-1");
        assertThat(found).isPresent();
        assertThat(found.get().isActive()).isFalse();

        List<CronJob> activeJobs = cronJobRepository.findByActiveTrue();
        List<CronJob> inactiveJobs = cronJobRepository.findByActiveFalse();

        assertThat(activeJobs).isEmpty();
        assertThat(inactiveJobs).hasSize(1);
        assertThat(cronJobRepository.countByActiveTrue()).isZero();
    }

    @Test
    void testDelete() {
        // Given
        cronJobRepository.save(activeJob1);
        cronJobRepository.save(inactiveJob1);
        entityManager.flush();

        // When
        cronJobRepository.deleteById("active-job-1");
        entityManager.flush();

        // Then
        assertThat(cronJobRepository.findById("active-job-1")).isEmpty();
        assertThat(cronJobRepository.findById("inactive-job-1")).isPresent();
        assertThat(cronJobRepository.count()).isEqualTo(1);
    }

    @Test
    void testFindAll() {
        // Given
        cronJobRepository.save(activeJob1);
        cronJobRepository.save(activeJob2);
        cronJobRepository.save(inactiveJob1);
        cronJobRepository.save(inactiveJob2);
        entityManager.flush();

        // When
        List<CronJob> allJobs = cronJobRepository.findAll();

        // Then
        assertThat(allJobs).hasSize(4);
        assertThat(allJobs).extracting(CronJob::getName)
                .containsExactlyInAnyOrder("active-job-1", "active-job-2", "inactive-job-1", "inactive-job-2");
    }

    @Test
    void testCount() {
        // Given
        cronJobRepository.save(activeJob1);
        cronJobRepository.save(activeJob2);
        cronJobRepository.save(inactiveJob1);
        entityManager.flush();

        // When
        long totalCount = cronJobRepository.count();

        // Then
        assertThat(totalCount).isEqualTo(3);
    }

    @Test
    void testExistsById() {
        // Given
        cronJobRepository.save(activeJob1);
        entityManager.flush();

        // When & Then
        assertThat(cronJobRepository.existsById("active-job-1")).isTrue();
        assertThat(cronJobRepository.existsById("non-existent-job")).isFalse();
    }

    @Test
    void testSaveAll() {
        // Given
        List<CronJob> jobsToSave = List.of(activeJob1, activeJob2, inactiveJob1);

        // When
        List<CronJob> savedJobs = cronJobRepository.saveAll(jobsToSave);
        entityManager.flush();

        // Then
        assertThat(savedJobs).hasSize(3);
        assertThat(cronJobRepository.count()).isEqualTo(3);
        assertThat(cronJobRepository.countByActiveTrue()).isEqualTo(2);
    }

    @Test
    void testDeleteAll() {
        // Given
        cronJobRepository.save(activeJob1);
        cronJobRepository.save(activeJob2);
        cronJobRepository.save(inactiveJob1);
        entityManager.flush();

        // When
        cronJobRepository.deleteAll();
        entityManager.flush();

        // Then
        assertThat(cronJobRepository.count()).isZero();
        assertThat(cronJobRepository.findByActiveTrue()).isEmpty();
        assertThat(cronJobRepository.findByActiveFalse()).isEmpty();
    }

    @Test
    void testComplexScenario_MixedOperations() {
        // Given - Start with some jobs
        cronJobRepository.save(activeJob1);
        cronJobRepository.save(inactiveJob1);
        entityManager.flush();

        // When - Perform various operations
        // Add more jobs
        cronJobRepository.save(activeJob2);
        cronJobRepository.save(inactiveJob2);

        // Update existing job
        activeJob1.setActive(false);
        cronJobRepository.save(activeJob1);

        // Delete one job
        cronJobRepository.deleteById("inactive-job-1");
        entityManager.flush();

        // Then - Verify final state
        assertThat(cronJobRepository.count()).isEqualTo(3);
        assertThat(cronJobRepository.countByActiveTrue()).isEqualTo(1); // Only activeJob2
        assertThat(cronJobRepository.findByActiveTrue()).hasSize(1);
        assertThat(cronJobRepository.findByActiveFalse()).hasSize(2); // activeJob1 (now inactive) and inactiveJob2

        List<CronJob> allJobs = cronJobRepository.findAll();
        assertThat(allJobs).extracting(CronJob::getName)
                .containsExactlyInAnyOrder("active-job-1", "active-job-2", "inactive-job-2");
    }
}
