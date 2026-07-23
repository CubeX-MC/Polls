package com.polls.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

enum PollTemplate {
    NORMAL("normal"),
    RENTAL("rental"),
    LOAN("loan");

    private final String id;

    PollTemplate(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }

    String textKey(String suffix) {
        return "submit.templates." + id + "." + suffix;
    }

    boolean hasPresetOptions() {
        return this != NORMAL;
    }

    static Optional<PollTemplate> fromId(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PollTemplate template : values()) {
            if (template.id.equals(normalized)) return Optional.of(template);
        }
        return Optional.empty();
    }

    static List<PollTemplate> configured(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of(values());

        List<PollTemplate> templates = new ArrayList<>();
        for (String id : ids) {
            fromId(id).ifPresent(template -> {
                if (!templates.contains(template)) templates.add(template);
            });
        }
        return templates.isEmpty() ? List.of(NORMAL) : List.copyOf(templates);
    }
}
