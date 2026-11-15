package lt.dotask.domain;

import java.time.Duration;
import java.time.Instant;

/** Vienos užduoties darbo atkarpa; jos trukmė skaičiuojama realiai praėjusiomis minutėmis. */
public record Session(long taskId, Instant start, Instant end) {
    public int minutes() {
        return Math.toIntExact(Duration.between(start, end).toMinutes());
    }
}
