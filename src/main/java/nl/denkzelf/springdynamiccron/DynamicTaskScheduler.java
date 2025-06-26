package nl.denkzelf.springdynamiccron;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicTaskScheduler {

    private final TaskScheduler taskScheduler;
    private final DatabaseCronService databaseCronService;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        databaseCronService.setCronJobsChangeListener(this::updateSchedules);
        scheduleAllTasks();
    }

    private void scheduleAllTasks() {
        List<CronJob> activeJobs = databaseCronService.getActiveCronJobs();
        updateSchedules(activeJobs);
    }

    private void updateSchedules(List<CronJob> cronJobs) {
        log.info("Updating schedules for {} cron jobs", cronJobs.size());
        cancelAllTasks();
        cronJobs.forEach(this::scheduleTask);
        log.info("Successfully updated schedules. Active tasks: {}", scheduledTasks.size());
    }

    private void scheduleTask(CronJob cronJob) {
        try {
            if (!isValidCronExpression(cronJob.getCronExpression())) {
                log.error("Invalid cron expression for task '{}': {}",
                        cronJob.getName(), cronJob.getCronExpression());
                return;
            }

            ScheduledFuture<?> scheduledTask = taskScheduler.schedule(
                    () -> executeJob(cronJob),
                    new CronTrigger(cronJob.getCronExpression())
            );

            scheduledTasks.put(cronJob.getName(), scheduledTask);
            log.info("Scheduled task '{}' with cron expression: {}",
                    cronJob.getName(), cronJob.getCronExpression());
        } catch (Exception e) {
            log.error("Error scheduling task '{}' with cron expression '{}': {}",
                    cronJob.getName(), cronJob.getCronExpression(), e.getMessage());
        }
    }

    private boolean isValidCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return false;
        }

        try {
            CronExpression.parse(cronExpression);
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid cron expression '{}': {}", cronExpression, e.getMessage());
            return false;
        }
    }

    private void executeJob(CronJob cronJob) {
        try {
            log.info("Executing job '{}' at {} with description: {}",
                    cronJob.getName(), LocalDateTime.now(), cronJob.getDescription());

            // Here you would typically instantiate and execute the job class
            // For now, we'll just log the execution
            // You can extend this to use reflection to instantiate cronJob.getJobClass()

        } catch (Exception e) {
            log.error("Error executing job '{}': {}", cronJob.getName(), e.getMessage());
        }
    }

    private void cancelAllTasks() {
        for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
            try {
                entry.getValue().cancel(false);
                log.debug("Cancelled task: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("Error cancelling task '{}': {}", entry.getKey(), e.getMessage());
            }
        }
        scheduledTasks.clear();
    }

    public void manualRefresh() {
        log.info("Manual refresh requested");
        scheduleAllTasks();
    }

    public Map<String, Boolean> getTaskStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
            status.put(entry.getKey(), !entry.getValue().isCancelled() && !entry.getValue().isDone());
        }
        return status;
    }
}
