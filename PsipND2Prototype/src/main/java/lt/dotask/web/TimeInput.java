package lt.dotask.web;

import lt.dotask.domain.PlanningSettings;
import lt.dotask.domain.ValidationException;
import java.time.*;
import java.time.format.DateTimeParseException;

/** API datos tekstą paverčia Instant, aiškiai tikrindama Vilniaus laiko poslinkį ir tikslumą. */
public final class TimeInput {
    private TimeInput() { }

    public static Instant parse(String field, String value) {
        if (value == null || value.isBlank()) throw new ValidationException(field + ": nurodykite datą ir laiką.");
        try {
            OffsetDateTime explicit = OffsetDateTime.parse(value);
            if (!PlanningSettings.ZONE.getRules().getValidOffsets(explicit.toLocalDateTime()).contains(explicit.getOffset())) {
                throw new ValidationException(field + ": poslinkis neatitinka Europe/Vilnius laiko juostos.");
            }
            return requireDatabasePrecision(field, explicit.toInstant());
        } catch (DateTimeParseException ignored) {
            try {
                LocalDateTime local = LocalDateTime.parse(value);
                var offsets = PlanningSettings.ZONE.getRules().getValidOffsets(local);
                // Ties laikrodžio persukimu nespėliojame, kurį iš dviejų laikų vartotojas turėjo omenyje.
                if (offsets.size() != 1) {
                    throw new ValidationException(field + ": dėl laikrodžio persukimo laikas neegzistuoja arba yra dviprasmis; pateikite galiojantį laiką su poslinkiu, pvz., +02:00.");
                }
                return requireDatabasePrecision(field, local.toInstant(offsets.getFirst()));
            } catch (DateTimeParseException invalid) {
                throw new ValidationException(field + ": naudokite ISO datą ir laiką, pvz., 2026-10-05T18:00+03:00.");
            }
        }
    }

    private static Instant requireDatabasePrecision(String field, Instant value) {
        // PostgreSQL saugo mikrosekundes, todėl smulkesnės įvesties neapvaliname nepastebimai.
        if (value.getNano() % 1000 != 0) {
            throw new ValidationException(field + ": palaikomas ne smulkesnis nei mikrosekundės tikslumas (6 skaitmenys po sekundės).");
        }
        return value;
    }
}
