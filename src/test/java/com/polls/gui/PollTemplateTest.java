package com.polls.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollTemplateTest {

    @Test
    void parsesIdsWithoutDependingOnCaseOrWhitespace() {
        assertEquals(PollTemplate.RENTAL, PollTemplate.fromId(" Rental ").orElseThrow());
        assertEquals(PollTemplate.LOAN, PollTemplate.fromId("LOAN").orElseThrow());
        assertTrue(PollTemplate.fromId("unknown").isEmpty());
        assertTrue(PollTemplate.fromId(null).isEmpty());
    }

    @Test
    void missingConfigurationEnablesAllTemplatesForExistingConfigs() {
        assertEquals(List.of(PollTemplate.values()), PollTemplate.configured(List.of()));
    }

    @Test
    void configuredTemplatesKeepOrderRemoveDuplicatesAndIgnoreInvalidIds() {
        assertEquals(List.of(PollTemplate.LOAN, PollTemplate.NORMAL),
                PollTemplate.configured(List.of("loan", "bad", "normal", "LOAN")));
        assertEquals(List.of(PollTemplate.NORMAL), PollTemplate.configured(List.of("bad")));
    }

    @Test
    void onlySpecializedTemplatesHavePresetOptions() {
        assertFalse(PollTemplate.NORMAL.hasPresetOptions());
        assertTrue(PollTemplate.RENTAL.hasPresetOptions());
        assertTrue(PollTemplate.LOAN.hasPresetOptions());
    }
}
