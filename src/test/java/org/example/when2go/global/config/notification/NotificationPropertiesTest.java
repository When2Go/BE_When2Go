package org.example.when2go.global.config.notification;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void scheduleDefaultsContainDrainSettings() {
        NotificationProperties properties = new NotificationProperties();

        assertThat(properties.getSchedule().getClaimSize()).isEqualTo(500);
        assertThat(properties.getSchedule().getMaxDrainSize()).isEqualTo(5000);
    }

    @Test
    void scheduleDrainSettingsAreValidated() {
        NotificationProperties properties = new NotificationProperties();
        properties.getSchedule().setClaimSize(0);
        properties.getSchedule().setMaxDrainSize(0);

        Set<ConstraintViolation<NotificationProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("schedule.claimSize", "schedule.maxDrainSize");
    }
}
