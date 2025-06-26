package nl.denkzelf.springdynamiccron;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class DatabaseCronServiceTest {

    @Mock
    private CronJobRepository cronJobRepository;

    @Mock
    private Consumer<List<CronJob>> cronJobsChangeListener;

    @InjectMocks
    private DatabaseCronService databaseCronService;

    private CronJob job1;
    private CronJob job2;
    private CronJob job3;
    private CronJob inactiveJob;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        job1 = CronJob.builder()
                .name("job-1")
                .cronExpression("0 */5 * * * *")
                .description("Job 1 description")
                .jobClass("com.example.Job1")
                .active(true)
                .createdAt(now)
                .build();

        job2 = CronJob.builder()
                .name("job-2")
                .cronExpression("0 0 * * * *")
                .description("Job 2 description")
                .jobClass("com.example.Job2")
                .active(true)
                .createdAt(now)
                .build();

        job3 = CronJob.builder()
                .name("job-3")
                .cronExpression("0 0 0 * * *")
                .description("Job 3 description")
                .jobClass("com.example.Job3")
                .active(true)
                .createdAt(now)
                .build();

        inactiveJob = CronJob.builder()
                .name("inactive-job")
                .cronExpression("0 0 12 * * *")
                .description("Inactive job description")
                .jobClass("com.example.InactiveJob")
                .active(false)
                .createdAt(now)
                .build();
        clearLastKnownJobs();
    }

    @Test
    void testSetCronJobsChangeListener() {
        // When
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);

        // Then
        Consumer<List<CronJob>> listener = (Consumer<List<CronJob>>) ReflectionTestUtils.getField(databaseCronService, "cronJobsChangeListener");
        assertThat(listener).isEqualTo(cronJobsChangeListener);
    }

    @Test
    void testGetActiveCronJobs() {
        // Given
        List<CronJob> activeJobs = Arrays.asList(job1, job2, job3);
        when(cronJobRepository.findByActiveTrue()).thenReturn(activeJobs);

        // When
        List<CronJob> result = databaseCronService.getActiveCronJobs();

        // Then
        assertThat(result).isEqualTo(activeJobs).hasSize(3);
        verify(cronJobRepository).findByActiveTrue();
    }

    @Test
    void testGetActiveCronJobs_EmptyList() {
        // Given
        when(cronJobRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        // When
        List<CronJob> result = databaseCronService.getActiveCronJobs();

        // Then
        assertThat(result).isEmpty();
        verify(cronJobRepository).findByActiveTrue();
    }

    @Test
    void testGetAllCronJobs() {
        // Given
        List<CronJob> allJobs = Arrays.asList(job1, job2, job3, inactiveJob);
        when(cronJobRepository.findAll()).thenReturn(allJobs);

        // When
        List<CronJob> result = databaseCronService.getAllCronJobs();

        // Then
        assertThat(result).isEqualTo(allJobs).hasSize(4);
        verify(cronJobRepository).findAll();
    }

    @Test
    void testGetAllCronJobs_EmptyList() {
        // Given
        when(cronJobRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        List<CronJob> result = databaseCronService.getAllCronJobs();

        // Then
        assertThat(result).isEmpty();
        verify(cronJobRepository).findAll();
    }

    @Test
    void testCheckForDatabaseChanges_InitialRun_NoListener() {
        // Given
        List<CronJob> activeJobs = Arrays.asList(job1, job2);
        when(cronJobRepository.findByActiveTrue()).thenReturn(activeJobs);

        // When
        databaseCronService.checkForDatabaseChanges();

        // Then
        verify(cronJobRepository).findByActiveTrue();
        Map<String, CronJob> lastKnownJobs = getLastKnownJobs();
        assertThat(lastKnownJobs).hasSize(2).containsKeys("job-1", "job-2");
    }

    @Test
    void testCheckForDatabaseChanges_InitialRun_WithListener() {
        // Given
        List<CronJob> activeJobs = Arrays.asList(job1, job2);
        when(cronJobRepository.findByActiveTrue()).thenReturn(activeJobs);
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);

        // When
        databaseCronService.checkForDatabaseChanges();

        // Then
        verify(cronJobRepository).findByActiveTrue();
        verify(cronJobsChangeListener).accept(activeJobs);
        Map<String, CronJob> lastKnownJobs = getLastKnownJobs();
        assertThat(lastKnownJobs).hasSize(2);
    }

    @Test
    void testCheckForDatabaseChanges_NoChanges() {
        // Given - First run to establish baseline
        List<CronJob> activeJobs = Arrays.asList(job1, job2);
        when(cronJobRepository.findByActiveTrue()).thenReturn(activeJobs);
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);
        databaseCronService.checkForDatabaseChanges();
        reset(cronJobsChangeListener);

        // When - Second run with same data
        databaseCronService.checkForDatabaseChanges();

        // Then
        verify(cronJobRepository, times(2)).findByActiveTrue();
        verify(cronJobsChangeListener, never()).accept(any());
    }

    @Test
    void testCheckForDatabaseChanges_NewJobAdded() {
        // Given - First run with 2 jobs
        List<CronJob> initialJobs = Arrays.asList(job1, job2);
        when(cronJobRepository.findByActiveTrue()).thenReturn(initialJobs);
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);
        databaseCronService.checkForDatabaseChanges();
        reset(cronJobsChangeListener);

        // When - Second run with 3 jobs
        List<CronJob> updatedJobs = Arrays.asList(job1, job2, job3);
        when(cronJobRepository.findByActiveTrue()).thenReturn(updatedJobs);
        databaseCronService.checkForDatabaseChanges();

        // Then
        verify(cronJobsChangeListener).accept(updatedJobs);
        Map<String, CronJob> lastKnownJobs = getLastKnownJobs();
        assertThat(lastKnownJobs).hasSize(3).containsKeys("job-1", "job-2", "job-3");
    }

    @Test
    void testCheckForDatabaseChanges_JobRemoved() {
        // Given - First run with 3 jobs
        List<CronJob> initialJobs = Arrays.asList(job1, job2, job3);
        when(cronJobRepository.findByActiveTrue()).thenReturn(initialJobs);
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);
        databaseCronService.checkForDatabaseChanges();
        reset(cronJobsChangeListener);

        // When - Second run with 2 jobs
        List<CronJob> updatedJobs = Arrays.asList(job1, job2);
        when(cronJobRepository.findByActiveTrue()).thenReturn(updatedJobs);
        databaseCronService.checkForDatabaseChanges();

        // Then
        verify(cronJobsChangeListener).accept(updatedJobs);
        Map<String, CronJob> lastKnownJobs = getLastKnownJobs();
        assertThat(lastKnownJobs).hasSize(2).containsKeys("job-1", "job-2");
    }

    @Test
    void testCheckForDatabaseChanges_JobCronExpressionChanged() {
        // Given - First run with original job
        List<CronJob> initialJobs = Collections.singletonList(job1);
        when(cronJobRepository.findByActiveTrue()).thenReturn(initialJobs);
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);
        databaseCronService.checkForDatabaseChanges();
        reset(cronJobsChangeListener);

        // When - Second run with modified cron expression
        CronJob modifiedJob = CronJob.builder()
                .name("job-1")
                .cronExpression("0 */10 * * * *") // Changed from */5 to */10
                .description("Job 1 description")
                .jobClass("com.example.Job1")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        List<CronJob> updatedJobs = Collections.singletonList(modifiedJob);
        when(cronJobRepository.findByActiveTrue()).thenReturn(updatedJobs);
        databaseCronService.checkForDatabaseChanges();

        // Then
        verify(cronJobsChangeListener).accept(updatedJobs);
    }

    @Test
    void testCheckForDatabaseChanges_JobActiveStatusChanged() {
        // Given - First run with active job
        List<CronJob> initialJobs = Collections.singletonList(job1);
        when(cronJobRepository.findByActiveTrue()).thenReturn(initialJobs);
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);
        databaseCronService.checkForDatabaseChanges();
        reset(cronJobsChangeListener);

        // When - Job becomes inactive (so it's no longer returned by findByActiveTrue)
        when(cronJobRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        databaseCronService.checkForDatabaseChanges();

        // Then
        ArgumentCaptor<List<CronJob>> captor = ArgumentCaptor.forClass(List.class);
        verify(cronJobsChangeListener).accept(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void testCheckForDatabaseChanges_RepositoryException() {
        // Given
        when(cronJobRepository.findByActiveTrue()).thenThrow(new RuntimeException("Database error"));
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> databaseCronService.checkForDatabaseChanges());
        verify(cronJobsChangeListener, never()).accept(any());
    }

    @Test
    void testCheckForDatabaseChanges_ListenerException() {
        // Given
        List<CronJob> activeJobs = Collections.singletonList(job1);
        when(cronJobRepository.findByActiveTrue()).thenReturn(activeJobs);
        doThrow(new RuntimeException("Listener error")).when(cronJobsChangeListener).accept(any());
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> databaseCronService.checkForDatabaseChanges());
    }

    @Test
    void testCheckForDatabaseChanges_MultipleChanges() {
        // Given - First run with 2 jobs
        List<CronJob> initialJobs = Arrays.asList(job1, job2);
        when(cronJobRepository.findByActiveTrue()).thenReturn(initialJobs);
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);
        databaseCronService.checkForDatabaseChanges();
        reset(cronJobsChangeListener);

        // When - Multiple changes: remove job1, add job3, modify job2
        CronJob modifiedJob2 = CronJob.builder()
                .name("job-2")
                .cronExpression("0 */15 * * * *") // Changed cron expression
                .description("Job 2 description")
                .jobClass("com.example.Job2")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        List<CronJob> updatedJobs = Arrays.asList(modifiedJob2, job3);
        when(cronJobRepository.findByActiveTrue()).thenReturn(updatedJobs);
        databaseCronService.checkForDatabaseChanges();

        // Then
        verify(cronJobsChangeListener).accept(updatedJobs);
        Map<String, CronJob> lastKnownJobs = getLastKnownJobs();
        assertThat(lastKnownJobs).hasSize(2).containsKeys("job-2", "job-3");
    }

    @Test
    void testCheckForDatabaseChanges_EmptyToNonEmpty() {
        // Given - First run with no jobs
        when(cronJobRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);
        databaseCronService.checkForDatabaseChanges();
        reset(cronJobsChangeListener);

        // When - Second run with jobs
        List<CronJob> updatedJobs = Arrays.asList(job1, job2);
        when(cronJobRepository.findByActiveTrue()).thenReturn(updatedJobs);
        databaseCronService.checkForDatabaseChanges();

        // Then
        verify(cronJobsChangeListener).accept(updatedJobs);
        Map<String, CronJob> lastKnownJobs = getLastKnownJobs();
        assertThat(lastKnownJobs).hasSize(2);
    }

    @Test
    void testCheckForDatabaseChanges_NonEmptyToEmpty() {
        // Given - First run with jobs
        List<CronJob> initialJobs = Arrays.asList(job1, job2);
        when(cronJobRepository.findByActiveTrue()).thenReturn(initialJobs);
        databaseCronService.setCronJobsChangeListener(cronJobsChangeListener);
        databaseCronService.checkForDatabaseChanges();
        reset(cronJobsChangeListener);

        // When - Second run with no jobs
        when(cronJobRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        databaseCronService.checkForDatabaseChanges();

        // Then
        ArgumentCaptor<List<CronJob>> captor = ArgumentCaptor.forClass(List.class);
        verify(cronJobsChangeListener).accept(captor.capture());
        assertThat(captor.getValue()).isEmpty();
        Map<String, CronJob> lastKnownJobs = getLastKnownJobs();
        assertThat(lastKnownJobs).isEmpty();
    }

    @Test
    void testJobEquals_SameJobs() {
        // Given
        CronJob job1Copy = CronJob.builder()
                .name("job-1")
                .cronExpression("0 */5 * * * *")
                .description("Different description") // This shouldn't matter
                .jobClass("com.example.Job1")
                .active(true)
                .createdAt(LocalDateTime.now().plusDays(1)) // This shouldn't matter
                .build();

        // Test using reflection since jobEquals is private
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(databaseCronService, "jobEquals", job1, job1Copy));

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testJobEquals_DifferentNames() {
        // Given
        CronJob differentNameJob = CronJob.builder()
                .name("different-name")
                .cronExpression("0 */5 * * * *")
                .description("Job 1 description")
                .jobClass("com.example.Job1")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        // Test using reflection
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(databaseCronService, "jobEquals", job1, differentNameJob));

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testJobEquals_DifferentCronExpressions() {
        // Given
        CronJob differentCronJob = CronJob.builder()
                .name("job-1")
                .cronExpression("0 */10 * * * *") // Different cron expression
                .description("Job 1 description")
                .jobClass("com.example.Job1")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        // Test using reflection
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(databaseCronService, "jobEquals", job1, differentCronJob));

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testJobEquals_DifferentActiveStatus() {
        // Given
        inactiveJob = CronJob.builder()
                .name("job-1")
                .cronExpression("0 */5 * * * *")
                .description("Job 1 description")
                .jobClass("com.example.Job1")
                .active(false) // Different active status
                .createdAt(LocalDateTime.now())
                .build();

        // Test using reflection
        boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(databaseCronService, "jobEquals", job1, inactiveJob));

        // Then
        assertThat(result).isFalse();
    }

    // Helper methods
    private void clearLastKnownJobs() {
        Map<String, CronJob> lastKnownJobs = (Map<String, CronJob>) ReflectionTestUtils.getField(databaseCronService, "lastKnownJobs");
        if (lastKnownJobs != null) {
            lastKnownJobs.clear();
        }
    }

    private Map<String, CronJob> getLastKnownJobs() {
        return (Map<String, CronJob>) ReflectionTestUtils.getField(databaseCronService, "lastKnownJobs");
    }
}
