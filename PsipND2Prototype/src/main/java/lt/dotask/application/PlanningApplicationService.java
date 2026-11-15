package lt.dotask.application;

import lt.dotask.domain.*;
import lt.dotask.persistence.PlanningRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Sujungia savininko duomenis, planuoklį ir saugojimą; pakeitimus valdo Spring transakcijos. */
@Service
@Transactional
public class PlanningApplicationService {
    private final PlanningRepository repository;
    private final SchedulingService scheduler;

    public PlanningApplicationService(PlanningRepository repository, SchedulingService scheduler) {
        this.repository = repository;
        this.scheduler = scheduler;
    }

    @Transactional(readOnly = true)
    public List<Task> tasks(DemoContext context) { return repository.tasks(context); }

    @Transactional(readOnly = true)
    public Task task(DemoContext context, long id) { return repository.task(context, positiveId(id)); }

    public Task saveTask(DemoContext context, Task value, boolean create) {
        repository.lockContext(context);
        // Netinkama įvestis turi būti atmesta dar prieš keičiant įrašą ar grafiko būseną.
        SchedulingService.validateTask(value);
        long id;
        if (create) {
            id = repository.createTask(context, value);
        } else {
            id = value.id();
            repository.updateTask(context, value);
        }
        repository.markStale(context);
        return repository.task(context, id);
    }

    public void deleteTask(DemoContext context, long id) {
        repository.lockContext(context);
        repository.deleteTask(context, positiveId(id));
        repository.markStale(context);
    }

    @Transactional(readOnly = true)
    public List<Availability> availability(DemoContext context) { return repository.availability(context); }

    @Transactional(readOnly = true)
    public Availability availability(DemoContext context, long id) { return repository.availability(context, positiveId(id)); }

    public Availability saveAvailability(DemoContext context, Availability value, boolean create) {
        repository.lockContext(context);
        SchedulingService.validateAvailability(value);
        long id;
        if (create) {
            id = repository.createAvailability(context, value);
        } else {
            id = value.id();
            repository.updateAvailability(context, value);
        }
        repository.markStale(context);
        return repository.availability(context, id);
    }

    public void deleteAvailability(DemoContext context, long id) {
        repository.lockContext(context);
        repository.deleteAvailability(context, positiveId(id));
        repository.markStale(context);
    }

    // Sesijos, likučiai ir metaduomenys perskaitomi iš tos pačios bazės būsenos.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SavedSchedule schedule(DemoContext context) { return repository.schedule(context); }

    /** Vienoje transakcijoje paruošia ir pakeičia tik aktyvios savininko poros grafiką. */
    public SavedSchedule recalculate(DemoContext context, Instant planningStart, int horizonDays) {
        repository.lockContext(context);
        List<Task> tasks = repository.tasks(context);
        PlanResult result = scheduler.calculate(tasks, repository.availability(context), planningStart, horizonDays, PlanningSettings.defaults());
        // Net ir apskaičiuoto rezultato nuorodos turi priklausyti įkeltoms šio konteksto užduotims.
        Set<Long> ownedIds = tasks.stream().map(Task::id).collect(Collectors.toSet());
        if (result.sessions().stream().anyMatch(session -> !ownedIds.contains(session.taskId()))
                || result.unscheduled().stream().anyMatch(value -> !ownedIds.contains(value.taskId()))) {
            throw new IllegalStateException("Planuoklis grąžino svetimą užduotį.");
        }
        // Naują grafiką išsaugome tik patikrinę visą rezultatą, toje pačioje transakcijoje.
        repository.replaceSchedule(context, planningStart, horizonDays, result);
        return repository.schedule(context);
    }

    public void initializeDemo() {
        for (DemoContext context : DemoContextResolver.CONTEXTS) {
            // Pakartotinis paleidimas neturi perrašyti jau redaguotų demo duomenų.
            if (!repository.contextExists(context)) {
                repository.createContext(context);
                seedFixture(context);
                recalculate(context, DemoFixtures.START, 3);
            }
        }
    }

    /** Atkuria tik žinomą demo rinkinį ir atsisako trinti naudotojo papildytą kontekstą. */
    public SavedSchedule resetDemo(DemoContext context) {
        repository.lockContext(context);
        List<Task> tasks = repository.tasks(context);
        List<Availability> availability = repository.availability(context);
        Set<Long> fixtureTasks = DemoFixtures.tasks(context).stream().map(Task::id).collect(Collectors.toSet());
        Set<Long> fixtureAvailability = DemoFixtures.availability(context).stream().map(Availability::id).collect(Collectors.toSet());
        if (tasks.stream().anyMatch(task -> !fixtureTasks.contains(task.id()))
                || availability.stream().anyMatch(value -> !fixtureAvailability.contains(value.id()))) {
            throw new DemoResetException();
        }
        repository.deleteSchedule(context);
        for (Task task : tasks) repository.deleteTask(context, task.id());
        for (Availability value : availability) repository.deleteAvailability(context, value.id());
        seedFixture(context);
        return recalculate(context, DemoFixtures.START, 3);
    }

    private void seedFixture(DemoContext context) {
        for (Task task : DemoFixtures.tasks(context)) repository.insertFixtureTask(context, task);
        for (Availability value : DemoFixtures.availability(context)) repository.insertFixtureAvailability(context, value);
    }

    private long positiveId(long id) {
        if (id <= 0) throw new ValidationException("id: identifikatorius turi būti teigiamas.");
        return id;
    }
}
