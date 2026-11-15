package lt.dotask.domain;

import java.util.List;

/** Algoritmo išvestis: suplanuotos sesijos ir kiekvienos neatliktos užduoties likutis. */
public record PlanResult(List<Session> sessions, List<Unscheduled> unscheduled) {
    public PlanResult {
        // Kopijos neleidžia vėliau pakeisti jau patikrinto rezultato sąrašų.
        sessions = List.copyOf(sessions);
        unscheduled = List.copyOf(unscheduled);
    }
}
