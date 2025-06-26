package nl.denkzelf.springdynamiccron;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestConfig {
    // This class can be used for additional test-specific beans if needed
}
