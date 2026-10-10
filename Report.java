package dev.sentinel.report;

import java.util.UUID;

public record Report(int id, UUID reportedUuid, String reportedName,
                     UUID reporterUuid, String reporterName,
                     String reason, String comment, long time) {
}
