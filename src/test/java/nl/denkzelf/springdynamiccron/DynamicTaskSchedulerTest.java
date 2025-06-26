package nl.denkzelf.springdynamiccron;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class DynamicTaskSchedulerTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private DatabaseCronService databaseCronService;

    @Mock
    private ScheduledFuture scheduledFuture1;

    @Mock
    private ScheduledFuture scheduledFuture2;

    @Mock
    private ScheduledFuture scheduledFuture3;

    @InjectMocks
    private DynamicTaskScheduler dynamicTaskScheduler;

    private CronJob validJob1;
    private CronJob validJob2;
    private CronJob invalidCronJob;
    private CronJob secondsBasedJob;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        validJob1 = CronJob.builder()
                .name("valid-job-1")
                .cronExpression("0 */5 * * * *")
                .description("Valid job 1 - every 5 minutes")
                .jobClass("com.example.ValidJob1")
                .active(true)
                .createdAt(now)
                .build();

        validJob2 = CronJob.builder()
                .name("valid-job-2")
                .cronExpression("0 0 * * * *")
                .description("Valid job 2 - every hour")
                .jobClass("com.example.ValidJob2")
                .active(true)
                .createdAt(now)
                .build();

        invalidCronJob = CronJob.builder()
                .name("invalid-job")
                .cronExpression("invalid-cron-expression")
                .description("Invalid cron job")
                .jobClass("com.example.InvalidJob")
                .active(true)
                .createdAt(now)
                .build();

        secondsBasedJob = CronJob.builder()
                .name("seconds-job")
                .cronExpression("*/10 * * * * *")
                .description("Job that runs every 10 seconds")
                .jobClass("com.example.SecondsJob")
                .active(true)
                .createdAt(now)
                .build();

        // Clear scheduled tasks map
        clearScheduledTasks();
    }

    @Test
    void testInitialize() {
        // Given
        List<CronJob> activeJobs = Arrays.asList(validJob1, validJob2);
        when(databaseCronService.getActiveCronJobs()).thenReturn(activeJobs);
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn(scheduledFuture1, scheduledFuture2);

        // When
        dynamicTaskScheduler.initialize();

        // Then
        verify(databaseCronService).setCronJobsChangeListener(any(Consumer.class));
        verify(databaseCronService).getActiveCronJobs();
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(CronTrigger.class));

        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).hasSize(2).containsKeys("valid-job-1", "valid-job-2");
    }

    @Test
    void testInitialize_EmptyJobList() {
        // Given
        when(databaseCronService.getActiveCronJobs()).thenReturn(Collections.emptyList());

        // When
        dynamicTaskScheduler.initialize();

        // Then
        verify(databaseCronService).setCronJobsChangeListener(any(Consumer.class));
        verify(databaseCronService).getActiveCronJobs();
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));

        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).isEmpty();
    }

    @Test
    void testInitialize_WithInvalidCronExpression() {
        // Given
        List<CronJob> jobs = Arrays.asList(validJob1, invalidCronJob);
        when(databaseCronService.getActiveCronJobs()).thenReturn(jobs);
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn(scheduledFuture1);

        // When
        dynamicTaskScheduler.initialize();

        // Then
        verify(databaseCronService).getActiveCronJobs();
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(CronTrigger.class)); // Only valid job scheduled

        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).hasSize(1).containsKey("valid-job-1").doesNotContainKey("invalid-job");
    }

    @Test
    void testInitialize_TaskSchedulerException() {
        // Given
        List<CronJob> activeJobs = Collections.singletonList(validJob1);
        when(databaseCronService.getActiveCronJobs()).thenReturn(activeJobs);
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenThrow(new RuntimeException("Scheduler error"));

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> dynamicTaskScheduler.initialize());

        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).isEmpty(); // Task not added due to exception
    }

    @Test
    void testManualRefresh() {
        // Given
        List<CronJob> activeJobs = Arrays.asList(validJob1, validJob2);
        when(databaseCronService.getActiveCronJobs()).thenReturn(activeJobs);
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn(scheduledFuture1, scheduledFuture2);

        // When
        dynamicTaskScheduler.manualRefresh();

        // Then
        verify(databaseCronService).getActiveCronJobs();
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(CronTrigger.class));

        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).hasSize(2);
    }

    @Test
    void testManualRefresh_CancelsExistingTasks() {
        // Given - First add some existing tasks
        Map<String, ScheduledFuture<?>> existingTasks = getScheduledTasks();
        existingTasks.put("existing-task", scheduledFuture1);

        List<CronJob> newJobs = Collections.singletonList(validJob2);
        when(databaseCronService.getActiveCronJobs()).thenReturn(newJobs);
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn(scheduledFuture2);

        // When
        dynamicTaskScheduler.manualRefresh();

        // Then
        verify(scheduledFuture1).cancel(false); // Existing task should be cancelled
        verify(databaseCronService).getActiveCronJobs();

        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).hasSize(1).containsKey("valid-job-2").doesNotContainKey("existing-task");
    }

    @Test
    void testGetTaskStatus_EmptyTasks() {
        // When
        Map<String, Boolean> taskStatus = dynamicTaskScheduler.getTaskStatus();

        // Then
        assertThat(taskStatus).isEmpty();
    }

    @Test
    void testGetTaskStatus_WithActiveTasks() {
        // Given
        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        scheduledTasks.put("task-1", scheduledFuture1);
        scheduledTasks.put("task-2", scheduledFuture2);

        when(scheduledFuture1.isCancelled()).thenReturn(false);
        when(scheduledFuture1.isDone()).thenReturn(false);
        when(scheduledFuture2.isCancelled()).thenReturn(false);
        when(scheduledFuture2.isDone()).thenReturn(false);

        // When
        Map<String, Boolean> taskStatus = dynamicTaskScheduler.getTaskStatus();

        // Then
        assertThat(taskStatus).hasSize(2);
        assertThat(taskStatus.get("task-1")).isTrue();
        assertThat(taskStatus.get("task-2")).isTrue();
    }

    @Test
    void testGetTaskStatus_WithCancelledTask() {
        // Given
        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        scheduledTasks.put("cancelled-task", scheduledFuture1);
        scheduledTasks.put("active-task", scheduledFuture2);

        when(scheduledFuture1.isCancelled()).thenReturn(true);
        when(scheduledFuture2.isCancelled()).thenReturn(false);
        when(scheduledFuture2.isDone()).thenReturn(false);

        // When
        Map<String, Boolean> taskStatus = dynamicTaskScheduler.getTaskStatus();

        // Then
        assertThat(taskStatus).hasSize(2);
        assertThat(taskStatus.get("cancelled-task")).isFalse();
        assertThat(taskStatus.get("active-task")).isTrue();
    }

    @Test
    void testGetTaskStatus_WithCompletedTask() {
        // Given
        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        scheduledTasks.put("completed-task", scheduledFuture1);

        when(scheduledFuture1.isCancelled()).thenReturn(false);
        when(scheduledFuture1.isDone()).thenReturn(true);

        // When
        Map<String, Boolean> taskStatus = dynamicTaskScheduler.getTaskStatus();

        // Then
        assertThat(taskStatus).hasSize(1);
        assertThat(taskStatus.get("completed-task")).isFalse();
    }

    @Test
    void testUpdateSchedules_NewJobs() {
        // Given
        List<CronJob> cronJobs = Arrays.asList(validJob1, validJob2);
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn(scheduledFuture1, scheduledFuture2);

        // When - Using reflection to call private method
        ReflectionTestUtils.invokeMethod(dynamicTaskScheduler, "updateSchedules", cronJobs);

        // Then
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(CronTrigger.class));

        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).hasSize(2).containsKeys("valid-job-1", "valid-job-2");
    }

    @Test
    void testUpdateSchedules_RemovesOldTasks() {
        // Given - Start with existing tasks
        Map<String, ScheduledFuture<?>> existingTasks = getScheduledTasks();
        existingTasks.put("old-task-1", scheduledFuture1);
        existingTasks.put("old-task-2", scheduledFuture2);

        List<CronJob> newJobs = Collections.singletonList(validJob1); // Only one new job
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn(scheduledFuture3);

        // When
        ReflectionTestUtils.invokeMethod(dynamicTaskScheduler, "updateSchedules", newJobs);

        // Then
        verify(scheduledFuture1).cancel(false);
        verify(scheduledFuture2).cancel(false);
        verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));

        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).hasSize(1).containsKey("valid-job-1");
    }

    @Test
    void testScheduleTask_ValidCronExpression() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn(scheduledFuture1);

        // When
        ReflectionTestUtils.invokeMethod(dynamicTaskScheduler, "scheduleTask", validJob1);

        // Then
        verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));

        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).hasSize(1).containsKey("valid-job-1");
        assertSame(scheduledFuture1, scheduledTasks.get("valid-job-1"));
    }

    @Test
    void testScheduleTask_InvalidCronExpression() {
        // When
        ReflectionTestUtils.invokeMethod(dynamicTaskScheduler, "scheduleTask", invalidCronJob);

        // Then
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));

        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).isEmpty();
    }

    @Test
    void testScheduleTask_SchedulerException() {
        // Given
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenThrow(new RuntimeException("Scheduling error"));

        // When & Then - Should not throw exception
        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(dynamicTaskScheduler, "scheduleTask", validJob1));

        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).isEmpty();
    }

    @Test
    void testIsValidCronExpression_ValidExpressions() {
        // Test various valid cron expressions
        String[] validExpressions = {
                "0 */5 * * * *",        // Every 5 minutes
                "0 0 * * * *",          // Every hour
                "0 0 0 * * *",          // Every day at midnight
                "0 0 0 * * SUN",        // Every Sunday at midnight
                "0 0 8-18 * * MON-FRI", // Every hour from 8-18 on weekdays
                "*/10 * * * * *"        // Every 10 seconds
        };

        for (String expression : validExpressions) {
            boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(
                    dynamicTaskScheduler, "isValidCronExpression", expression));
            assertThat(result).as("Expression should be valid: " + expression).isTrue();
        }
    }

    @Test
    void testIsValidCronExpression_InvalidExpressions() {
        // Test various invalid cron expressions
        String[] invalidExpressions = {
                "invalid-cron",
                "0 0 0 32 * *",         // Invalid day (32)
                "0 0 25 * * *",         // Invalid hour (25)
                "0 61 * * * *",         // Invalid minute (61)
                "",                     // Empty string
                null,                   // Null
                "0 0 * * * * *",        // Too many fields
                "* * *"                 // Too few fields
        };

        for (String expression : invalidExpressions) {
            boolean result = Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(
                    dynamicTaskScheduler, "isValidCronExpression", expression));
            assertThat(result).as("Expression should be invalid: " + expression).isFalse();
        }
    }

    @Test
    void testExecuteJob_ValidJobClass() {
        // Note: This test assumes executeJob tries to instantiate and execute the job class
        // The actual implementation may vary, so this test might need adjustment

        // When
        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(dynamicTaskScheduler, "executeJob", validJob1));

        // Then - Should not throw exception even if class doesn't exist
        // The method should handle ClassNotFoundException gracefully
    }

    @Test
    void testCancelAllTasks() {
        // Given
        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        scheduledTasks.put("task-1", scheduledFuture1);
        scheduledTasks.put("task-2", scheduledFuture2);

        // When
        ReflectionTestUtils.invokeMethod(dynamicTaskScheduler, "cancelAllTasks");

        // Then
        verify(scheduledFuture1).cancel(false);
        verify(scheduledFuture2).cancel(false);
        assertThat(scheduledTasks).isEmpty();
    }

    @Test
    void testCancelAllTasks_WithCancellationException() {
        // Given
        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        scheduledTasks.put("task-1", scheduledFuture1);
        scheduledTasks.put("task-2", scheduledFuture2);

        doThrow(new RuntimeException("Cancel error")).when(scheduledFuture1).cancel(false);

        // When & Then - Should not throw exception
        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(dynamicTaskScheduler, "cancelAllTasks"));

        verify(scheduledFuture1).cancel(false);
        verify(scheduledFuture2).cancel(false);
        assertThat(scheduledTasks).isEmpty();
    }

    @Test
    void testCronJobsChangeListener_Integration() {
        // Given
        List<CronJob> initialJobs = Collections.singletonList(validJob1);
        when(databaseCronService.getActiveCronJobs()).thenReturn(initialJobs);
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn(scheduledFuture1, scheduledFuture2);

        // Initialize to set up the listener
        dynamicTaskScheduler.initialize();

        // Capture the listener that was registered
        ArgumentCaptor<Consumer<List<CronJob>>> listenerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(databaseCronService).setCronJobsChangeListener(listenerCaptor.capture());
        Consumer<List<CronJob>> listener = listenerCaptor.getValue();

        // When - Trigger the listener with new jobs
        List<CronJob> updatedJobs = Collections.singletonList(validJob2);
        listener.accept(updatedJobs);

        // Then - Verify the scheduler was updated
        verify(scheduledFuture1).cancel(false);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    void testComplexScenario_MultipleOperations() {
        // Given - Initial setup
        List<CronJob> initialJobs = Arrays.asList(validJob1, validJob2);
        when(databaseCronService.getActiveCronJobs()).thenReturn(initialJobs);
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn(scheduledFuture1, scheduledFuture2, scheduledFuture3);

        // When - Initialize
        dynamicTaskScheduler.initialize();

        // Then - Check initial state
        Map<String, ScheduledFuture<?>> scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).hasSize(2);

        // When - Manual refresh with different jobs
        List<CronJob> newJobs = Arrays.asList(validJob1, secondsBasedJob); // Replace job2 with secondsBasedJob
        when(databaseCronService.getActiveCronJobs()).thenReturn(newJobs);
        dynamicTaskScheduler.manualRefresh();

        // Then - Verify state after refresh
        verify(scheduledFuture1).cancel(false); // job1's old future cancelled
        verify(scheduledFuture2).cancel(false); // job2's future cancelled
        scheduledTasks = getScheduledTasks();
        assertThat(scheduledTasks).hasSize(2).containsKeys("valid-job-1", "seconds-job");

        // When - Get task status
        when(scheduledFuture3.isCancelled()).thenReturn(false);
        when(scheduledFuture3.isDone()).thenReturn(false);

        Map<String, Boolean> taskStatus = dynamicTaskScheduler.getTaskStatus();

        // Then - Verify task status
        assertThat(taskStatus).hasSize(2);
        assertThat(taskStatus.get("valid-job-1")).isTrue();
        assertThat(taskStatus.get("seconds-job")).isTrue();
    }

    // Helper methods
    private void clearScheduledTasks() {
        Map<String, ScheduledFuture<?>> scheduledTasks =
                (Map<String, ScheduledFuture<?>>) ReflectionTestUtils.getField(dynamicTaskScheduler, "scheduledTasks");
        if (scheduledTasks != null) {
            scheduledTasks.clear();
        }
    }

    private Map<String, ScheduledFuture<?>> getScheduledTasks() {
        return (Map<String, ScheduledFuture<?>>) ReflectionTestUtils.getField(dynamicTaskScheduler, "scheduledTasks");
    }
}
