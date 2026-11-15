package lt.dotask.domain;

import java.util.List;

/** Užduoties nesuplanuotos minutės ir galimos priežastys; 0 reiškia, kad visas darbas tilpo. */
public record Unscheduled(long taskId, int minutes, List<String> reasons) {
    public Unscheduled {
        // Priežasčių sąrašas negali būti pakeistas po rezultato sukūrimo.
        reasons = List.copyOf(reasons);
    }
}
