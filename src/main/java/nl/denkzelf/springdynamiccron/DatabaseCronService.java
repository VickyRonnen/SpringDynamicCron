package nl.denkzelf.springdynamiccron;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseCronService {

    private final CronJobRepository cronJobRepository;
    private final Map<String, CronJob> lastKnownJobs = new ConcurrentHashMap<>();
    @Setter
    private Consumer<List<CronJob>> cronJobsChangeListener;

    @Transactional(readOnly = true)
    public List<CronJob> getActiveCronJobs() {
        return cronJobRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<CronJob> getAllCronJobs() {
        return cronJobRepository.findAll();
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional(readOnly = true)
    public void checkForDatabaseChanges() {
        try {
            List<CronJob> currentJobs = cronJobRepository.findByActiveTrue();

            if (hasJobsChanged(currentJobs)) {
                log.info("Detected changes in cron jobs, updating schedules...");
                updateLastKnownJobs(currentJobs);

                if (cronJobsChangeListener != null) {
                    cronJobsChangeListener.accept(currentJobs);
                }
            }
        } catch (Exception e) {
            log.error("Error checking for database changes", e);
        }
    }

    private boolean hasJobsChanged(List<CronJob> currentJobs) {
        if (currentJobs.size() != lastKnownJobs.size()) {
            return true;
        }
        for (CronJob job : currentJobs) {
            CronJob lastKnown = lastKnownJobs.get(job.getName());
            if (lastKnown == null || !jobEquals(job, lastKnown)) {
                return true;
            }
        }
        return false;
    }

    private boolean jobEquals(CronJob job1, CronJob job2) {
        return job1.getName().equals(job2.getName()) &&
                job1.getCronExpression().equals(job2.getCronExpression()) &&
                job1.isActive() == job2.isActive();
    }

    private void updateLastKnownJobs(List<CronJob> jobs) {
        lastKnownJobs.clear();
        for (CronJob job : jobs) {
            lastKnownJobs.put(job.getName(), job);
        }
    }
}
