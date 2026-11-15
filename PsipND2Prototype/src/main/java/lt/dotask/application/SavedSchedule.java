package lt.dotask.application;

import lt.dotask.domain.Session;
import lt.dotask.domain.Unscheduled;
import java.time.Instant;
import java.util.List;

/** Išsaugotas grafikas su įvesties pradžia ir horizontu; stale nurodo, kad įvestis jau pasikeitė. */
public record SavedSchedule(Instant planningStart, Integer horizonDays, boolean stale,
                            List<Session> sessions, List<Unscheduled> unscheduled) {
}
