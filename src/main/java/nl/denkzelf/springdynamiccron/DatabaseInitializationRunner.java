package nl.denkzelf.springdynamiccron;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@Slf4j
@AllArgsConstructor
public class DatabaseInitializationRunner implements ApplicationRunner {

    private final CronJobRepository cronJobRepository;


    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Starting database initialization...");

        try {
            initializeCronJobs();
            logDatabaseStatistics();
        } catch (Exception e) {
            log.error("Error during database initialization", e);
            throw e;
        }

        log.info("Database initialization completed successfully");
    }

    private void initializeCronJobs() {
        log.info("Initializing cron jobs...");

        createCronJobIfNotExists(
                "system-health-check",
                "0 */5 * * * *",
                "System health check every 5 minutes",
                "nl.denkzelf.springdynamiccron.jobs.SystemHealthCheckJob"
        );

        createCronJobIfNotExists(
                "database-cleanup",
                "0 0 2 * * *",
                "Daily database cleanup at 2 AM",
                "nl.denkzelf.springdynamiccron.jobs.DatabaseCleanupJob"
        );

        createCronJobIfNotExists(
                "log-rotation",
                "0 0 0 * * SUN",
                "Weekly log rotation every Sunday at midnight",
                "nl.denkzelf.springdynamiccron.jobs.LogRotationJob"
        );

        createCronJobIfNotExists(
                "backup-task",
                "0 30 1 * * *",
                "Daily backup task at 1:30 AM",
                "nl.denkzelf.springdynamiccron.jobs.BackupJob"
        );

        createCronJobIfNotExists(
                "report-generation",
                "0 0 8 * * MON-FRI",
                "Generate daily reports on weekdays at 8 AM",
                "nl.denkzelf.springdynamiccron.jobs.ReportGenerationJob"
        );

        if (isDevEnvironment()) {
            createTestCronJobs();
        }
    }

    private void createCronJobIfNotExists(String name, String cronExpression, String description, String jobClass) {
        if (cronJobRepository.findById(name).isEmpty()) {
            CronJob cronJob = CronJob.builder()
                    .name(name)
                    .cronExpression(cronExpression)
                    .description(description)
                    .jobClass(jobClass)
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            cronJobRepository.save(cronJob);
            log.info("Created cron job: {} with expression: {}", name, cronExpression);
        } else {
            log.debug("Cron job already exists: {}", name);
        }
    }

    private void createTestCronJobs() {
        log.info("Creating test cron jobs for development environment...");

        createCronJobIfNotExists(
                "test-every-10-seconds",
                "*/10 * * * * *",
                "Test job running every 10 seconds",
                "nl.denkzelf.springdynamiccron.jobs.TestJob"
        );

        createCronJobIfNotExists(
                "test-every-minute",
                "0 * * * * *",
                "Test job running every minute",
                "nl.denkzelf.springdynamiccron.jobs.TestJob"
        );

        createInactiveCronJob(
                "inactive-test-job",
                "0 0 * * * *",
                "Inactive test job for testing purposes",
                "nl.denkzelf.springdynamiccron.jobs.InactiveTestJob"
        );
    }

    private void createInactiveCronJob(String name, String cronExpression, String description, String jobClass) {
        if (cronJobRepository.findById(name).isEmpty()) {
            CronJob cronJob = CronJob.builder()
                    .name(name)
                    .cronExpression(cronExpression)
                    .description(description)
                    .jobClass(jobClass)
                    .active(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            cronJobRepository.save(cronJob);
            log.info("Created inactive cron job: {}", name);
        }
    }

    private boolean isDevEnvironment() {
        String activeProfile = System.getProperty("spring.profiles.active", "");
        return activeProfile.contains("dev") || activeProfile.contains("development") || activeProfile.isEmpty();
    }

    private void logDatabaseStatistics() {
        long totalJobs = cronJobRepository.count();
        long activeJobs = cronJobRepository.countByActiveTrue();
        long inactiveJobs = totalJobs - activeJobs;

        log.info("Database statistics:");
        log.info("  Total cron jobs: {}", totalJobs);
        log.info("  Active cron jobs: {}", activeJobs);
        log.info("  Inactive cron jobs: {}", inactiveJobs);
    }
}
