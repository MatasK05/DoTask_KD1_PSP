package lt.dotask.application;

import lt.dotask.domain.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Fiksuota dviejų klientų pavyzdinė įvestis; grafikus iš jos apskaičiuoja tikras algoritmas. */
public final class DemoFixtures {
    // Spalio data nekinta kartu su kompiuterio data, todėl I plano pavyzdys visada atkuriamas.
    public static final Instant START = time("2026-10-05T18:00");

    private DemoFixtures() { }

    public static List<Task> tasks(DemoContext context) {
        if (context.equals(DemoContextResolver.FACULTY)) {
            return List.of(
                    new Task(101, "Java laboratorinis (A)", time("2026-10-07T21:30"), Priority.HIGH, 240, false),
                    new Task(102, "UX ataskaita (B)", time("2026-10-06T21:30"), Priority.MEDIUM, 120, false));
        }
        return List.of(new Task(201, "Klubo renginio planas", time("2026-10-06T20:00"), Priority.LOW, 90, false));
    }

    public static List<Availability> availability(DemoContext context) {
        if (context.equals(DemoContextResolver.FACULTY)) {
            return List.of(new Availability(1001, time("2026-10-05T18:00"), time("2026-10-05T21:30")),
                    new Availability(1002, time("2026-10-06T18:00"), time("2026-10-06T21:30")));
        }
        return List.of(new Availability(2001, time("2026-10-06T17:00"), time("2026-10-06T20:00")));
    }

    private static Instant time(String value) {
        return LocalDateTime.parse(value).atZone(PlanningSettings.ZONE).toInstant();
    }
}
