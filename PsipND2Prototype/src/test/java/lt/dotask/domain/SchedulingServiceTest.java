package lt.dotask.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static lt.dotask.domain.Priority.*;
import static org.junit.jupiter.api.Assertions.*;

/** Be Spring ir bazės lygina tikslų algoritmo rezultatą su fiksuotais pavyzdžiais bei ribiniais atvejais. */
class SchedulingServiceTest {
    private final SchedulingService scheduler = new SchedulingService(TaskComparators.deadlineFirst());
    private final PlanningSettings settings = PlanningSettings.defaults();

    @Test
    void baseExampleMatchesAllFourSessionsAndSharedDailyLimit() {
        List<Task> tasks = baseTasks();
        PlanResult result = plan(tasks, baseAvailability());
        assertEquals(List.of(session(2, "05T18:00", "05T20:00"),
                session(1, "05T20:15", "05T21:15"), session(1, "06T18:00", "06T20:00"),
                session(1, "06T20:15", "06T21:15")), result.sessions());
        assertEquals(List.of(120, 60, 120, 60), result.sessions().stream().map(Session::minutes).toList());
        assertEquals(List.of(new Unscheduled(1, 0, List.of()), new Unscheduled(2, 0, List.of())), result.unscheduled());
        assertEquals(240, total(result, 1));
        assertEquals(120, total(result, 2));
        assertEquals(baseTasks(), tasks, "Planavimas nekeičia likusio darbo ar būsenos.");
    }

    @Test
    void equalDeadlinesUseHigherPriorityAndPreserveBreak() {
        PlanResult result = plan(List.of(task(1, 60, MEDIUM, "05T20:15"), task(2, 60, HIGH, "05T20:15")),
                List.of(availability(1, "05T18:00", "05T20:15")));
        assertEquals(List.of(session(2, "05T18:00", "05T19:00"), session(1, "05T19:15", "05T20:15")), result.sessions());
        assertEquals(List.of(0, 0), result.unscheduled().stream().map(Unscheduled::minutes).toList());
    }

    @Test
    void shortageLeavesExactly120Minutes() {
        PlanResult result = plan(List.of(task(1, 240, HIGH, "05T20:00")),
                List.of(availability(1, "05T18:00", "05T20:00")));
        assertEquals(List.of(session(1, "05T18:00", "05T20:00")), result.sessions());
        assertEquals(new Unscheduled(1, 120, List.of(SchedulingService.TIME_SHORTAGE)), result.unscheduled().getFirst());
    }

    @Test
    void dailyLimitLeavesExactly60Minutes() {
        PlanResult result = plan(List.of(task(1, 240, HIGH, "05T22:30")),
                List.of(availability(1, "05T18:00", "05T22:30")));
        assertEquals(List.of(session(1, "05T18:00", "05T20:00"), session(1, "05T20:15", "05T21:15")), result.sessions());
        assertEquals(60, result.unscheduled().getFirst().minutes());
        assertTrue(result.unscheduled().getFirst().reasons().contains(SchedulingService.DAILY_LIMIT));
        assertEquals(180, total(result, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {-15, 0, 14, 16})
    void rejectsInvalidUnfinishedMinutes(int minutes) {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> plan(List.of(task(1, minutes, HIGH, "05T20:00")), baseAvailability()));
        assertTrue(exception.getMessage().startsWith("Likusi trukmė:"));
    }

    @Test
    void removingSecondDayLeaves180MinutesForA() {
        PlanResult result = plan(baseTasks(), List.of(baseAvailability().getFirst()));
        assertEquals(List.of(session(2, "05T18:00", "05T20:00"), session(1, "05T20:15", "05T21:15")), result.sessions());
        assertEquals(180, result.unscheduled().getFirst().minutes());
        assertEquals(0, result.unscheduled().get(1).minutes());
    }

    @Test
    void equalPriorityAndDeadlineUseStableLowestId() {
        PlanResult result = plan(List.of(task(9, 60, HIGH, "05T20:15"), task(3, 60, HIGH, "05T20:15")),
                List.of(availability(1, "05T18:00", "05T20:15")));
        assertEquals(List.of(session(3, "05T18:00", "05T19:00"), session(9, "05T19:15", "05T20:15")), result.sessions());
    }

    @Test
    void completedTasksAreOmittedAndElapsedDeadlinesRemainUnscheduled() {
        List<Task> tasks = List.of(new Task(1, "Atlikta", time("05T20:00"), LOW, 0, true),
                new Task(2, "Atlikta su senu likučiu", time("05T20:00"), HIGH, 120, true),
                task(3, 60, HIGH, "04T20:00"), task(4, 30, MEDIUM, "05T18:00"));
        PlanResult result = plan(tasks, baseAvailability());
        assertEquals(List.of(), result.sessions());
        assertEquals(List.of(new Unscheduled(3, 60, List.of(SchedulingService.ELAPSED_DEADLINE)),
                new Unscheduled(4, 30, List.of(SchedulingService.ELAPSED_DEADLINE))), result.unscheduled());
    }

    @Test
    void overlappingAvailabilityIsMergedWithoutDuplicatedWorkTime() {
        PlanResult result = plan(List.of(task(1, 180, HIGH, "05T21:30")), List.of(
                availability(1, "05T18:00", "05T19:00"), availability(2, "05T18:30", "05T20:00"),
                availability(3, "05T19:30", "05T21:30")));
        assertEquals(List.of(session(1, "05T18:00", "05T20:00"), session(1, "05T20:15", "05T21:15")), result.sessions());
        assertEquals(0, result.unscheduled().getFirst().minutes());
    }

    @Test
    void roundsStartUpAndUsableDurationDown() {
        PlanResult result = scheduler.calculate(List.of(task(1, 120, HIGH, "05T20:00")),
                List.of(availability(1, "05T17:00", "05T19:07")), time("05T18:00:01"), 1, settings);
        assertEquals(List.of(session(1, "05T18:15", "05T19:00")), result.sessions());
        assertEquals(75, result.unscheduled().getFirst().minutes());
        assertEquals(time("05T18:00"), SchedulingService.roundUp(time("05T18:00")));
    }

    @Test
    void subQuarterGapsProduceNoSessionsAndFinishNormally() {
        PlanResult result = plan(List.of(task(1, 15, HIGH, "05T20:00")),
                List.of(availability(1, "05T18:01", "05T18:29")));
        assertEquals(List.of(), result.sessions());
        assertEquals(15, result.unscheduled().getFirst().minutes());
    }

    @Test
    void midnightSplitsDailyWorkAndPreservesBreak() {
        PlanResult result = scheduler.calculate(List.of(task(1, 120, HIGH, "06T02:00")),
                List.of(availability(1, "05T23:00", "06T02:00")), time("05T23:00"), 1,
                new PlanningSettings(120, 15, 60));
        assertEquals(List.of(session(1, "05T23:00", "06T00:00"), session(1, "06T00:15", "06T01:15")), result.sessions());
        assertEquals(0, result.unscheduled().getFirst().minutes());
    }

    @Test
    void horizonEndsExactlyAfterLocalCalendarDays() {
        PlanResult result = scheduler.calculate(List.of(task(1, 180, HIGH, "07T20:00")),
                List.of(availability(1, "06T17:00", "06T21:00")), time("05T18:00"), 1, settings);
        assertEquals(List.of(session(1, "06T17:00", "06T18:00")), result.sessions());
        assertEquals(120, result.unscheduled().getFirst().minutes());
        assertTrue(result.unscheduled().getFirst().reasons().contains(SchedulingService.HORIZON_LIMIT));
    }

    @Test
    void freeGapBeforeLaterSessionReservesBreakOnBothSides() {
        var window = new SchedulingService.TimeWindow(time("05T18:00"), time("05T22:00"));
        var gaps = SchedulingService.freeGaps(window,
                List.of(session(1, "05T19:00", "05T20:00")), 15);
        assertEquals(List.of(new SchedulingService.TimeWindow(time("05T18:00"), time("05T18:45")),
                new SchedulingService.TimeWindow(time("05T20:15"), time("05T22:00"))), gaps);
        // Įterpiama sesija iki 18:45 palieka pertrauką prieš jau esančią 19:00 sesiją.
        assertEquals(45, new Session(2, gaps.getFirst().start(), gaps.getFirst().end()).minutes());
    }

    @Test
    void naturalGapAlreadyProvidesBreak() {
        PlanResult result = plan(List.of(task(1, 120, HIGH, "05T22:00")),
                List.of(availability(1, "05T18:00", "05T19:00"), availability(2, "05T19:30", "05T20:30")));
        assertEquals(List.of(session(1, "05T18:00", "05T19:00"), session(1, "05T19:30", "05T20:30")), result.sessions());
    }

    @Test
    void recalculationIsDeterministicAndDoesNotConsumeRemainingWork() {
        var tasks = new ArrayList<>(baseTasks());
        var availability = new ArrayList<>(baseAvailability());
        PlanResult first = plan(tasks, availability);
        assertEquals(first, plan(tasks, availability));
        assertEquals(240, tasks.getFirst().remainingMinutes());
        assertFalse(tasks.getFirst().done());
    }

    @Test
    void priorityFirstComparatorChangesOrderWithoutChangingAllocator() {
        List<Task> tasks = List.of(task(1, 60, LOW, "06T21:30"), task(2, 60, HIGH, "07T21:30"));
        List<Availability> availability = List.of(availability(1, "05T18:00", "05T20:15"));
        assertEquals(List.of(session(1, "05T18:00", "05T19:00"), session(2, "05T19:15", "05T20:15")),
                plan(tasks, availability).sessions());
        PlanResult alternative = new SchedulingService(TaskComparators.priorityFirst())
                .calculate(tasks, availability, time("05T18:00"), 3, settings);
        assertEquals(List.of(session(2, "05T18:00", "05T19:00"), session(1, "05T19:15", "05T20:15")), alternative.sessions());
        assertEquals(List.of(0, 0), alternative.unscheduled().stream().map(Unscheduled::minutes).toList());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 31})
    void rejectsHorizonOutsideOneToThirtyDays(int days) {
        assertTrue(assertThrows(ValidationException.class, () -> scheduler.calculate(baseTasks(), baseAvailability(),
                time("05T18:00"), days, settings)).getMessage().startsWith("Laikotarpis:"));
    }

    @Test
    void rejects1001TasksButAccepts1000() {
        List<Task> tasks = LongStream.rangeClosed(1, 1000).mapToObj(id -> task(id, 15, LOW, "05T20:00")).toList();
        assertEquals(1000, plan(tasks, List.of()).unscheduled().size());
        List<Task> tooMany = new ArrayList<>(tasks);
        tooMany.add(task(1001, 15, LOW, "05T20:00"));
        assertTrue(assertThrows(ValidationException.class, () -> plan(tooMany, List.of())).getMessage().contains("1000"));
    }

    @Test
    void rejectsMalformedRecordsAndSettings() {
        assertThrows(ValidationException.class, () -> plan(List.of(task(0, 15, LOW, "05T20:00")), List.of()));
        assertThrows(ValidationException.class, () -> plan(List.of(new Task(1, " ", time("05T20:00"), LOW, 15, false)), List.of()));
        assertThrows(ValidationException.class, () -> plan(List.of(new Task(1, "A", null, LOW, 15, false)), List.of()));
        assertThrows(ValidationException.class, () -> plan(List.of(new Task(1, "A", time("05T20:00"), null, 15, false)), List.of()));
        assertThrows(ValidationException.class, () -> plan(List.of(), List.of(availability(1, "05T20:00", "05T20:00"))));
        assertThrows(ValidationException.class, () -> plan(List.of(baseTasks().getFirst(), baseTasks().getFirst()), List.of()));
        for (PlanningSettings invalid : List.of(new PlanningSettings(0, 15, 180), new PlanningSettings(16, 15, 180),
                new PlanningSettings(120, -15, 180), new PlanningSettings(120, 1, 180), new PlanningSettings(120, 15, 0))) {
            assertThrows(ValidationException.class, () -> scheduler.calculate(baseTasks(), baseAvailability(), time("05T18:00"), 3, invalid));
        }
    }

    @Test
    void explicitOffsetsAcrossAutumnClockChangeUseElapsedMinutes() {
        Instant start = Instant.parse("2026-10-25T00:00:00Z");
        Instant end = Instant.parse("2026-10-25T03:00:00Z");
        Task task = new Task(1, "Laikrodžio keitimas", end, HIGH, 120, false);
        PlanResult result = scheduler.calculate(List.of(task), List.of(new Availability(1, start, end)), start, 1, settings);
        assertEquals(List.of(new Session(1, start, Instant.parse("2026-10-25T02:00:00Z"))), result.sessions());
        assertEquals(120, result.sessions().getFirst().minutes());
        assertEquals(0, result.unscheduled().getFirst().minutes());
    }

    private PlanResult plan(List<Task> tasks, List<Availability> availability) {
        return scheduler.calculate(tasks, availability, time("05T18:00"), 3, settings);
    }

    private static List<Task> baseTasks() {
        return List.of(task(1, 240, HIGH, "07T21:30"), task(2, 120, MEDIUM, "06T21:30"));
    }

    private static List<Availability> baseAvailability() {
        return List.of(availability(1, "05T18:00", "05T21:30"), availability(2, "06T18:00", "06T21:30"));
    }

    private static Task task(long id, int minutes, Priority priority, String deadline) {
        return new Task(id, id == 1 ? "Java laboratorinis" : "UX ataskaita", time(deadline), priority, minutes, false);
    }

    private static Availability availability(long id, String start, String end) {
        return new Availability(id, time(start), time(end));
    }

    private static Session session(long id, String start, String end) {
        return new Session(id, time(start), time(end));
    }

    private static Instant time(String dayAndTime) {
        return LocalDateTime.parse("2026-10-" + dayAndTime).atZone(PlanningSettings.ZONE).toInstant();
    }

    private static int total(PlanResult result, long taskId) {
        return result.sessions().stream().filter(session -> session.taskId() == taskId).mapToInt(Session::minutes).sum();
    }
}
