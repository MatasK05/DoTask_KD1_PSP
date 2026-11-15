package lt.dotask.domain;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static lt.dotask.domain.PlanningSettings.ZONE;

/** Grynas Java planuoklis: tikrina įvestį ir godžiai skirsto laiką be HTTP, Spring ar duomenų bazės. */
public class SchedulingService {
    public static final String TIME_SHORTAGE = "Pagal pasirinktą eilę trūksta tinkamo prieinamumo iki termino; įtaką gali turėti pertraukos ir kiti apribojimai.";
    public static final String DAILY_LIMIT = "Pasiektas dienos darbo limitas bent vieną tinkamą dieną.";
    public static final String HORIZON_LIMIT = "Planavimo laikotarpis baigiasi anksčiau negu užduoties terminas.";
    public static final String ELAPSED_DEADLINE = "Terminas jau praėjo arba sutampa su planavimo pradžia; teigiamo darbo laiko neliko.";

    private final Comparator<Task> taskOrder;

    public SchedulingService() {
        this(TaskComparators.deadlineFirst());
    }

    public SchedulingService(Comparator<Task> taskOrder) {
        this.taskOrder = Objects.requireNonNull(taskOrder);
    }

    /** Apskaičiuoja naują rezultatą nekeisdamas užduočių ar ankstesnio išsaugoto grafiko. */
    public PlanResult calculate(List<Task> tasks, List<Availability> availability,
                                Instant planningStart, int horizonDays, PlanningSettings settings) {
        validateInput(tasks, availability, planningStart, horizonDays, settings);
        Instant start = roundUp(planningStart);
        Instant horizonEnd = start.atZone(ZONE).plusDays(horizonDays).toInstant();
        List<TimeWindow> windows = normalize(availability, start, horizonEnd);
        List<Session> sessions = new ArrayList<>();
        List<Unscheduled> unscheduled = new ArrayList<>();
        // Vienos dienos limitą dalijasi visos šio naudotojo užduotys.
        Map<LocalDate, Integer> dailyMinutes = new HashMap<>();

        // Eilę nustato perduotas Comparator, todėl skirstymo ciklui nereikia žinoti jos politikos.
        List<Task> orderedTasks = tasks.stream().filter(task -> !task.done()).sorted(taskOrder).toList();
        for (Task task : orderedTasks) {
            if (!task.deadline().isAfter(planningStart)) {
                unscheduled.add(new Unscheduled(task.id(), task.remainingMinutes(), List.of(ELAPSED_DEADLINE)));
                continue;
            }
            int remaining = task.remainingMinutes();
            boolean dailyLimitReached = false;
            for (TimeWindow window : windows) {
                if (remaining == 0 || !window.start().isBefore(task.deadline())) {
                    break;
                }
                LocalDate date = window.start().atZone(ZONE).toLocalDate();
                Instant end = earlier(window.end(), task.deadline());
                // Kiekviena užduotis vėl peržiūri ankstyviausius tarpus, įskaitant tarpus prieš kitas sesijas.
                for (TimeWindow gap : freeGaps(new TimeWindow(window.start(), end), sessions, settings.breakMinutes())) {
                    Instant cursor = roundUp(gap.start());
                    while (remaining > 0 && cursor.isBefore(gap.end())) {
                        int dailyRemaining = settings.dailyLimitMinutes() - dailyMinutes.getOrDefault(date, 0);
                        if (dailyRemaining == 0) {
                            dailyLimitReached = true;
                            break;
                        }
                        long availableMinutes = Duration.between(cursor, gap.end()).toMinutes();
                        // Sesiją riboja likęs darbas, sesijos maksimumas, dienos likutis ir laisvas tarpas.
                        int minutes = (int) Math.min(Math.min(remaining, settings.sessionMaxMinutes()),
                                Math.min(dailyRemaining, availableMinutes));
                        minutes -= minutes % 15;
                        if (minutes < 15) {
                            // Trumpas tarpas netinka net mažiausiai sesijai, todėl jo nebetikriname iš naujo.
                            break;
                        }
                        Instant sessionEnd = cursor.plus(minutes, ChronoUnit.MINUTES);
                        sessions.add(new Session(task.id(), cursor, sessionEnd));
                        dailyMinutes.merge(date, minutes, Integer::sum);
                        remaining -= minutes;
                        // Pertrauka pastumia kitą pradžią, bet nedidina darbo minučių sumos.
                        cursor = sessionEnd.plus(settings.breakMinutes(), ChronoUnit.MINUTES);
                    }
                }
                if (remaining > 0 && dailyMinutes.getOrDefault(date, 0) == settings.dailyLimitMinutes()) {
                    dailyLimitReached = true;
                }
            }
            List<String> reasons = new ArrayList<>();
            if (remaining > 0) {
                // Kelios ribos gali veikti kartu; likutis neįrodo, kad kitokia eilė nieko nepagerintų.
                reasons.add(TIME_SHORTAGE);
                if (dailyLimitReached) {
                    reasons.add(DAILY_LIMIT);
                }
                if (task.deadline().isAfter(horizonEnd)) {
                    reasons.add(HORIZON_LIMIT);
                }
            }
            unscheduled.add(new Unscheduled(task.id(), remaining, reasons));
        }
        sessions.sort(Comparator.comparing(Session::start).thenComparingLong(Session::taskId));
        unscheduled.sort(Comparator.comparingLong(Unscheduled::taskId));
        PlanResult result = new PlanResult(sessions, unscheduled);
        // Naują grafiką leidžiame išsaugoti tik patikrinę visą rezultatą.
        validateResult(tasks, windows, start, horizonEnd, settings, result);
        return result;
    }

    /** Tas pačias užduoties taisykles taikome ir redagavimui, ir iš bazės gautai planavimo įvesčiai. */
    public static void validateTask(Task task) {
        require(task != null, "Užduotis: privaloma pateikti reikšmę.");
        require(task.id() > 0, "Užduoties ID: turi būti teigiamas.");
        require(task.title() != null && !task.title().isBlank(), "Pavadinimas: privalomas.");
        require(task.title().length() <= 200, "Pavadinimas: daugiausia 200 simbolių.");
        validateTime(task.deadline(), "Terminas");
        require(task.priority() != null, "Prioritetas: pasirinkite LOW, MEDIUM arba HIGH.");
        if (task.done()) {
            // Atliktas darbas neplanuojamas, todėl jam leidžiama ir nulinė likusi trukmė.
            require(task.remainingMinutes() >= 0 && task.remainingMinutes() % 15 == 0,
                    "Likusi trukmė: atliktai užduočiai galima 0 arba neneigiamas 15 minučių kartotinis.");
        } else {
            positiveQuarter(task.remainingMinutes(), "Likusi trukmė");
        }
    }

    public static void validateAvailability(Availability availability) {
        require(availability != null, "Prieinamumas: privaloma pateikti intervalą.");
        require(availability.id() > 0, "Prieinamumo ID: turi būti teigiamas.");
        validateTime(availability.start(), "Prieinamumo pradžia");
        validateTime(availability.end(), "Prieinamumo pabaiga");
        require(availability.start().isBefore(availability.end()),
                "Prieinamumo pabaiga: turi būti vėlesnė už pradžią.");
    }

    /** Prieš skaičiavimą tikrina dydžio ribas, nustatymus ir visų įrašų tinkamumą. */
    private static void validateInput(List<Task> tasks, List<Availability> availability,
                                      Instant start, int horizonDays, PlanningSettings settings) {
        require(tasks != null, "Užduotys: privalomas sąrašas.");
        require(tasks.size() <= 1000, "Užduotys: vienu skaičiavimu galima daugiausia 1000 užduočių.");
        require(availability != null, "Prieinamumas: privalomas sąrašas.");
        require(horizonDays >= 1 && horizonDays <= 30, "Laikotarpis: turi būti nuo 1 iki 30 dienų.");
        validateTime(start, "Planavimo pradžia");
        require(settings != null, "Nustatymai: privalomi.");
        positiveQuarter(settings.sessionMaxMinutes(), "Sesijos maksimumas");
        positiveQuarter(settings.dailyLimitMinutes(), "Dienos limitas");
        require(settings.breakMinutes() >= 0 && settings.breakMinutes() % 15 == 0,
                "Pertrauka: turi būti neneigiamas 15 minučių kartotinis.");
        Set<Long> taskIds = new HashSet<>();
        for (Task task : tasks) {
            validateTask(task);
            require(taskIds.add(task.id()), "Užduoties ID: sąraše negali kartotis.");
        }
        Set<Long> availabilityIds = new HashSet<>();
        for (Availability interval : availability) {
            validateAvailability(interval);
            require(availabilityIds.add(interval.id()), "Prieinamumo ID: sąraše negali kartotis.");
        }
    }

    private static void validateTime(Instant time, String field) {
        require(time != null, field + ": data ir laikas privalomi.");
        try {
            int year = time.atZone(ZONE).getYear();
            require(year >= 1900 && year <= 9998, field + ": palaikomi metai nuo 1900 iki 9998.");
        } catch (DateTimeException exception) {
            throw new ValidationException(field + ": netinkama data arba laikas.");
        }
    }

    private static void positiveQuarter(int minutes, String field) {
        require(minutes > 0 && minutes % 15 == 0, field + ": turi būti teigiamas 15 minučių kartotinis.");
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new ValidationException(message);
        }
    }

    /** Pakelia pradžią iki artimiausios 15 min. ribos; jau tiksli pradžia nesikeičia. */
    static Instant roundUp(Instant time) {
        ZonedDateTime local = time.atZone(ZONE);
        ZonedDateTime floor = local.withMinute(local.getMinute() / 15 * 15).withSecond(0).withNano(0);
        return floor.toInstant().equals(time) ? time : floor.plusMinutes(15).toInstant();
    }

    /** Apkerpa laiką pagal horizontą, sujungia persidengimus ir padalija pagal vietines dienas. */
    private static List<TimeWindow> normalize(List<Availability> availability, Instant start, Instant end) {
        List<TimeWindow> clipped = availability.stream()
                .map(interval -> new TimeWindow(later(interval.start(), start), earlier(interval.end(), end)))
                .filter(window -> window.start().isBefore(window.end()))
                .sorted(Comparator.comparing(TimeWindow::start)).toList();
        List<TimeWindow> merged = new ArrayList<>();
        for (TimeWindow window : clipped) {
            if (merged.isEmpty() || merged.getLast().end().isBefore(window.start())) {
                merged.add(window);
            } else {
                TimeWindow previous = merged.removeLast();
                merged.add(new TimeWindow(previous.start(), later(previous.end(), window.end())));
            }
        }
        List<TimeWindow> days = new ArrayList<>();
        for (TimeWindow window : merged) {
            Instant cursor = window.start();
            while (cursor.isBefore(window.end())) {
                Instant midnight = cursor.atZone(ZONE).toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant();
                Instant dayEnd = earlier(window.end(), midnight);
                days.add(new TimeWindow(cursor, dayEnd));
                cursor = dayEnd;
            }
        }
        return days;
    }

    /** Iš intervalo pašalina esamas sesijas su pertraukos atsarga prieš jas ir po jų. */
    static List<TimeWindow> freeGaps(TimeWindow window, List<Session> sessions, int breakMinutes) {
        List<Session> orderedSessions = sessions.stream().sorted(Comparator.comparing(Session::start)).toList();
        List<TimeWindow> gaps = new ArrayList<>();
        Instant cursor = window.start();
        for (Session session : orderedSessions) {
            Instant blockedStart = session.start().minus(breakMinutes, ChronoUnit.MINUTES);
            Instant blockedEnd = session.end().plus(breakMinutes, ChronoUnit.MINUTES);
            if (!blockedEnd.isAfter(cursor)) {
                continue;
            }
            if (!blockedStart.isBefore(window.end())) {
                break;
            }
            // Pakankamas natūralus tarpas jau atstoja pertrauką abiejose sesijos pusėse.
            if (cursor.isBefore(blockedStart)) {
                gaps.add(new TimeWindow(cursor, earlier(blockedStart, window.end())));
            }
            cursor = later(cursor, blockedEnd);
            if (!cursor.isBefore(window.end())) {
                break;
            }
        }
        if (cursor.isBefore(window.end())) {
            gaps.add(new TimeWindow(cursor, window.end()));
        }
        return gaps;
    }

    /** Patikrina jau gauto rezultato ribas ir minučių sumas; naujo grafiko ši patikra nekuria. */
    private static void validateResult(List<Task> tasks, List<TimeWindow> windows, Instant start,
                                       Instant end, PlanningSettings settings, PlanResult result) {
        Map<Long, Task> byId = new HashMap<>();
        tasks.forEach(task -> byId.put(task.id(), task));
        Map<Long, Long> scheduledMinutes = new HashMap<>();
        Map<LocalDate, Long> dailyMinutes = new HashMap<>();
        Session previous = null;
        for (Session session : result.sessions()) {
            Task task = byId.get(session.taskId());
            check(task != null && !task.done(), "Sesija nurodo netinkamą užduotį.");
            long seconds = Duration.between(session.start(), session.end()).getSeconds();
            check(seconds > 0 && seconds % 900 == 0 && session.start().getNano() == 0 && session.end().getNano() == 0,
                    "Sesijos trukmė neatitinka 15 minučių ribos.");
            check(roundUp(session.start()).equals(session.start()), "Sesijos pradžia neatitinka 15 minučių ribos.");
            check(!session.start().isBefore(start) && !session.end().isAfter(end), "Sesija už planavimo laikotarpio.");
            check(!session.end().isAfter(task.deadline()), "Sesija viršija terminą.");
            check(session.minutes() <= settings.sessionMaxMinutes(), "Sesija viršija maksimumą.");
            check(windows.stream().anyMatch(window -> !session.start().isBefore(window.start())
                    && !session.end().isAfter(window.end())), "Sesija nepatenka į dienos prieinamumą.");
            if (previous != null) {
                check(!session.start().isBefore(previous.end().plus(settings.breakMinutes(), ChronoUnit.MINUTES)),
                        "Sesijos persidengia arba neišlaikyta pertrauka.");
            }
            scheduledMinutes.merge(task.id(), (long) session.minutes(), Long::sum);
            dailyMinutes.merge(session.start().atZone(ZONE).toLocalDate(), (long) session.minutes(), Long::sum);
            previous = session;
        }
        check(dailyMinutes.values().stream().allMatch(minutes -> minutes <= settings.dailyLimitMinutes()),
                "Viršytas dienos darbo limitas.");
        Map<Long, Integer> unscheduledMinutes = new HashMap<>();
        for (Unscheduled entry : result.unscheduled()) {
            Task task = byId.get(entry.taskId());
            check(task != null && !task.done(), "Likutis nurodo netinkamą užduotį.");
            check(entry.minutes() >= 0 && entry.minutes() % 15 == 0, "Netinkamas nesuplanuotas likutis.");
            check(unscheduledMinutes.put(entry.taskId(), entry.minutes()) == null, "Pasikartojantis užduoties likutis.");
            check(entry.minutes() == 0 || !entry.reasons().isEmpty(), "Trūksta likučio paaiškinimo.");
        }
        for (Task task : tasks) {
            if (!task.done()) {
                check(unscheduledMinutes.containsKey(task.id()), "Trūksta užduoties likučio.");
                check(scheduledMinutes.getOrDefault(task.id(), 0L) + unscheduledMinutes.get(task.id()) == task.remainingMinutes(),
                        "Nesutampa užduoties minučių suma.");
            }
        }
    }

    private static void check(boolean valid, String message) {
        if (!valid) {
            throw new IllegalStateException("Neteisingas planavimo rezultatas: " + message);
        }
    }

    private static Instant earlier(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static Instant later(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    /** Vidinis laiko tarpas [start, end), naudojamas normalizuotam prieinamumui ir laisviems tarpams. */
    record TimeWindow(Instant start, Instant end) { }
}
