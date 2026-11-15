package lt.dotask.domain;

import java.time.ZoneId;

/** Planavimo ribos minutėmis; dienos ir laikrodžio ribos skaičiuojamos pagal Vilniaus laiką. */
public record PlanningSettings(int sessionMaxMinutes, int breakMinutes, int dailyLimitMinutes) {
    public static final ZoneId ZONE = ZoneId.of("Europe/Vilnius");

    public static PlanningSettings defaults() {
        // Abu demo klientai naudoja tuos pačius I etapo plano nustatymus.
        return new PlanningSettings(120, 15, 180);
    }
}
