package lt.dotask.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lt.dotask.application.*;
import lt.dotask.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Priima HTTP įvestį, nustato demo kontekstą ir perduoda darbą taikomajai paslaugai. */
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
public class PlanningController {
    private final PlanningApplicationService service;
    private final DemoContextResolver contexts;

    public PlanningController(PlanningApplicationService service, DemoContextResolver contexts) {
        this.service = service;
        this.contexts = contexts;
    }

    @GetMapping("/context")
    public ContextResponse context(@RequestHeader(value = "X-Demo-Context", required = false) String key) {
        var context = contexts.resolve(key);
        return new ContextResponse(context.key(), context.name(), context.tenantId(), context.userId(), "Europe/Vilnius", PlanningSettings.defaults());
    }

    @GetMapping("/tasks")
    public List<Task> tasks(@RequestHeader(value = "X-Demo-Context", required = false) String key) { return service.tasks(contexts.resolve(key)); }

    @GetMapping("/tasks/{id}")
    public Task task(@RequestHeader(value = "X-Demo-Context", required = false) String key, @PathVariable long id) { return service.task(contexts.resolve(key), id); }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public Task createTask(@RequestHeader(value = "X-Demo-Context", required = false) String key, @Valid @RequestBody TaskRequest body) {
        // 1 čia tik laikinas validavimo ID; tikrą naujo įrašo ID priskiria duomenų bazė.
        return service.saveTask(contexts.resolve(key), body.toTask(1), true);
    }

    @PutMapping("/tasks/{id}")
    public Task updateTask(@RequestHeader(value = "X-Demo-Context", required = false) String key, @PathVariable long id, @Valid @RequestBody TaskRequest body) {
        DemoContext context = contexts.resolve(key);
        service.task(context, id);
        return service.saveTask(context, body.toTask(id), false);
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@RequestHeader(value = "X-Demo-Context", required = false) String key, @PathVariable long id) { service.deleteTask(contexts.resolve(key), id); }

    @GetMapping("/availability")
    public List<Availability> availability(@RequestHeader(value = "X-Demo-Context", required = false) String key) { return service.availability(contexts.resolve(key)); }

    @GetMapping("/availability/{id}")
    public Availability availability(@RequestHeader(value = "X-Demo-Context", required = false) String key, @PathVariable long id) { return service.availability(contexts.resolve(key), id); }

    @PostMapping("/availability")
    @ResponseStatus(HttpStatus.CREATED)
    public Availability createAvailability(@RequestHeader(value = "X-Demo-Context", required = false) String key, @Valid @RequestBody AvailabilityRequest body) {
        return service.saveAvailability(contexts.resolve(key), body.toAvailability(1), true);
    }

    @PutMapping("/availability/{id}")
    public Availability updateAvailability(@RequestHeader(value = "X-Demo-Context", required = false) String key, @PathVariable long id, @Valid @RequestBody AvailabilityRequest body) {
        DemoContext context = contexts.resolve(key);
        service.availability(context, id);
        return service.saveAvailability(context, body.toAvailability(id), false);
    }

    @DeleteMapping("/availability/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvailability(@RequestHeader(value = "X-Demo-Context", required = false) String key, @PathVariable long id) { service.deleteAvailability(contexts.resolve(key), id); }

    @GetMapping("/schedule")
    public SavedSchedule schedule(@RequestHeader(value = "X-Demo-Context", required = false) String key) { return service.schedule(contexts.resolve(key)); }

    @PostMapping("/plan")
    public SavedSchedule plan(@RequestHeader(value = "X-Demo-Context", required = false) String key, @Valid @RequestBody PlanRequest body) {
        // Užduotys imamos iš serverio pagal kontekstą, o ne iš naršyklės pateikto svetimų ID sąrašo.
        return service.recalculate(contexts.resolve(key), TimeInput.parse("planningStart", body.planningStart()), body.horizonDays());
    }

    @PostMapping("/demo/reset")
    public SavedSchedule reset(@RequestHeader(value = "X-Demo-Context", required = false) String key) { return service.resetDemo(contexts.resolve(key)); }

    /** Puslapiui skirta aktyvaus konteksto ir nekeičiamų nustatymų išvestis. */
    public record ContextResponse(String key, String name, long tenantId, long userId, String timezone, PlanningSettings settings) { }
    /** Vieno skaičiavimo parametrai; užduotys ir laisvas laikas jau saugomi duomenų bazėje. */
    public record PlanRequest(String planningStart, @NotNull(message = "nurodykite planavimo dienų skaičių") Integer horizonDays) { }
    /** Leidžiami intervalo JSON laukai, be klientui leidžiamos keisti savininko poros. */
    public record AvailabilityRequest(String start, String end) {
        Availability toAvailability(long id) { return new Availability(id, TimeInput.parse("start", start), TimeInput.parse("end", end)); }
    }
    /** Leidžiami užduoties JSON laukai; tekstinis prioritetas toTask metode tampa Java enum. */
    public record TaskRequest(String title, String deadline, String priority,
                              @NotNull(message = "nurodykite likusias minutes") Integer remainingMinutes,
                              @NotNull(message = "nurodykite atlikimo būseną") Boolean done) {
        Task toTask(long id) {
            Priority parsed;
            try { parsed = Priority.valueOf(priority == null ? "" : priority); }
            catch (IllegalArgumentException error) { throw new ValidationException("priority: pasirinkite LOW, MEDIUM arba HIGH."); }
            return new Task(id, title, TimeInput.parse("deadline", deadline), parsed, remainingMinutes, done);
        }
    }
}
