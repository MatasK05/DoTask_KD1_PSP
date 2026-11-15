package lt.dotask.domain;

import java.time.Instant;

/** Planavimo įvestis; remainingMinutes reiškia neatliktą darbą, o ne dar nesuplanuotas minutes. */
public record Task(long id, String title, Instant deadline, Priority priority,
                   int remainingMinutes, boolean done) { }
