package lt.dotask.domain;

import java.time.Instant;

/** Laisvas darbo intervalas [start, end): pradžia įtraukiama, pabaiga neįtraukiama. */
public record Availability(long id, Instant start, Instant end) { }
