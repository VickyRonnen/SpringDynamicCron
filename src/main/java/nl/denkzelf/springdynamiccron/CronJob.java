package nl.denkzelf.springdynamiccron;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CronJob {
    @Id
    private String name;
    private String cronExpression;
    private String description;
    private String jobClass;
    private boolean active;
    private LocalDateTime createdAt;
}
