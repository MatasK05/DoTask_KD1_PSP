package lt.dotask.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lt.dotask.domain.*;
import lt.dotask.persistence.PlanningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static lt.dotask.application.DemoContextResolver.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tikrina HTTP sluoksnį, tikrą SQL saugojimą, transakcijų atšaukimą ir savininkų izoliaciją. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseGuard.class)
class PlanningApiIT {
    private static final String PLAN = "{\"planningStart\":\"2026-10-05T18:00+03:00\",\"horizonDays\":3}";
    private static final String VALID_TASK = "{\"title\":\"Pakeista\",\"deadline\":\"2026-10-07T21:30+03:00\",\"priority\":\"HIGH\",\"remainingMinutes\":60,\"done\":false}";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlanningApplicationService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean PlanningRepository repository;

    @BeforeEach
    void restoreDedicatedFixtures() {
        reset(repository);
        for (DemoContext context : CONTEXTS) {
            // Tik testų bazėje šaliname ankstesnio testo sukurtus įrašus, kiekvieną pagal kontekstą.
            for (Task task : service.tasks(context)) {
                if (task.id() >= 10000) service.deleteTask(context, task.id());
            }
            for (Availability value : service.availability(context)) {
                if (value.id() >= 10000) service.deleteAvailability(context, value.id());
            }
            service.resetDemo(context);
        }
    }

    @Test
    void baseExampleIsPersistedExactlyAndRepeatedCalculationDoesNotAppendOrCompleteWork() throws Exception {
        JsonNode first = request(post("/api/plan").content(PLAN), "fakultetas", 200);
        assertThat(first.path("sessions")).isEqualTo(json.readTree("""
                [{"taskId":102,"start":"2026-10-05T15:00:00Z","end":"2026-10-05T17:00:00Z"},
                 {"taskId":101,"start":"2026-10-05T17:15:00Z","end":"2026-10-05T18:15:00Z"},
                 {"taskId":101,"start":"2026-10-06T15:00:00Z","end":"2026-10-06T17:00:00Z"},
                 {"taskId":101,"start":"2026-10-06T17:15:00Z","end":"2026-10-06T18:15:00Z"}]
                """));
        assertThat(first.path("unscheduled")).isEqualTo(json.readTree("[{\"taskId\":101,\"minutes\":0,\"reasons\":[]},{\"taskId\":102,\"minutes\":0,\"reasons\":[]}]"));
        assertThat(request(post("/api/plan").content(PLAN), "fakultetas", 200)).isEqualTo(first);
        assertThat(request(get("/api/schedule"), "fakultetas", 200)).isEqualTo(first);
        assertThat(service.task(FACULTY, 101).remainingMinutes()).isEqualTo(240);
        assertThat(service.task(FACULTY, 102).done()).isFalse();
    }

    @Test
    void removingSecondDayProduces180UnscheduledMinutes() throws Exception {
        SavedSchedule clubBefore = service.schedule(CLUB);
        request(delete("/api/availability/1002"), "fakultetas", 204);
        assertThat(service.schedule(FACULTY).stale()).isTrue();
        JsonNode result = request(post("/api/plan").content(PLAN), "fakultetas", 200);
        assertThat(result.path("sessions").size()).isEqualTo(2);
        assertThat(result.path("sessions").get(0).path("taskId").asInt()).isEqualTo(102);
        assertThat(result.path("sessions").get(1).path("start").asText()).isEqualTo("2026-10-05T17:15:00Z");
        assertThat(result.path("unscheduled").get(0).path("minutes").asInt()).isEqualTo(180);
        assertThat(result.path("unscheduled").get(1).path("minutes").asInt()).isZero();
        assertThat(result.path("unscheduled").get(0).path("reasons").toString()).contains("prieinamumo");
        assertThat(service.schedule(CLUB)).isEqualTo(clubBefore);
    }

    @Test
    void invalidEditAndInvalidCalculationLeaveExactSavedScheduleUnchanged() throws Exception {
        JsonNode before = request(get("/api/schedule"), "fakultetas", 200);
        Task taskBefore = service.task(FACULTY, 101);
        JsonNode error = request(put("/api/tasks/101").content(VALID_TASK.replace(":60,", ":-15,")), "fakultetas", 400);
        assertThat(error.path("message").asText()).contains("Likusi trukm", "15");
        assertThat(service.task(FACULTY, 101)).isEqualTo(taskBefore);
        assertThat(request(get("/api/schedule"), "fakultetas", 200)).isEqualTo(before);
        request(post("/api/plan").content(PLAN.replace(":3", ":31")), "fakultetas", 400);
        assertThat(request(get("/api/schedule"), "fakultetas", 200)).isEqualTo(before);
        // Sugadinta saugyklos įvestis parodo, kad skaičiavimas tikrina ir iš bazės gautą trukmę.
        jdbc.update("UPDATE task SET remaining_minutes=-15 WHERE id=101 AND tenant_id=1 AND owner_user_id=11");
        request(post("/api/plan").content(PLAN), "fakultetas", 400);
        assertThat(request(get("/api/schedule"), "fakultetas", 200)).isEqualTo(before);
    }

    @Test
    void failedReplacementRollsBackDeletionInsertionAndMetadata() {
        SavedSchedule before = service.schedule(FACULTY);
        doAnswer(call -> {
            // Pirma vykdomas tikras SQL pakeitimas, tada sukelta klaida turi atšaukti visą transakciją.
            call.callRealMethod();
            throw new IllegalStateException("Valdomas įrašymo sutrikimas teste");
        }).when(repository).replaceSchedule(eq(FACULTY), any(), anyInt(), any());
        assertThatThrownBy(() -> service.recalculate(FACULTY, DemoFixtures.START.plusSeconds(3600), 2)).isInstanceOf(IllegalStateException.class);
        assertThat(service.schedule(FACULTY)).isEqualTo(before);
    }

    @Test
    void foreignTaskCannotBeReadUpdatedOrDeletedThroughHttp() throws Exception {
        JsonNode clubBefore = request(get("/api/tasks/201"), "klubas", 200);
        JsonNode missing = request(get("/api/tasks/201"), "fakultetas", 404);
        assertThat(missing.toString()).doesNotContain("Klubo renginio planas");
        request(put("/api/tasks/201").content(VALID_TASK), "fakultetas", 404);
        request(delete("/api/tasks/201"), "fakultetas", 404);
        assertThat(request(get("/api/tasks/201"), "klubas", 200)).isEqualTo(clubBefore);
        assertThat(request(get("/api/tasks"), "fakultetas", 200).findValuesAsText("id")).containsExactly("101", "102");
        assertThat(request(get("/api/tasks"), "klubas", 200).findValuesAsText("id")).containsExactly("201");
    }

    @Test
    void foreignAvailabilityAndSchedulesAreScopedAndResetDoesNotTouchOtherClient() throws Exception {
        SavedSchedule clubBefore = service.schedule(CLUB);
        List<Availability> availabilityBefore = service.availability(CLUB);
        request(get("/api/availability/2001"), "fakultetas", 404);
        request(put("/api/availability/2001").content("{\"start\":\"2026-10-05T18:00\",\"end\":\"2026-10-05T19:00\"}"), "fakultetas", 404);
        request(delete("/api/availability/2001"), "fakultetas", 404);
        assertThat(request(get("/api/availability"), "fakultetas", 200).findValuesAsText("id")).containsExactly("1001", "1002");
        assertThat(request(get("/api/schedule"), "fakultetas", 200).path("sessions").findValuesAsText("taskId")).containsOnly("101", "102");
        request(post("/api/demo/reset"), "fakultetas", 200);
        assertThat(service.schedule(CLUB)).isEqualTo(clubBefore);
        assertThat(service.availability(CLUB)).isEqualTo(availabilityBefore);
    }

    @Test
    void ownershipFieldsAndUnknownContextsAreRejected() throws Exception {
        request(post("/api/tasks").content(VALID_TASK.replace("{", "{\"tenantId\":2,\"userId\":22,")), "fakultetas", 400);
        request(post("/api/plan").content(PLAN.replace("{", "{\"taskId\":201,")), "fakultetas", 400);
        request(get("/api/tasks/201/sessions"), "fakultetas", 404);
        request(get("/api/tasks"), "nezinomas", 400);
        mvc.perform(get("/api/tasks")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("X-Demo-Context")));
        assertThat(service.tasks(FACULTY)).hasSize(2);
        assertThat(service.tasks(CLUB)).hasSize(1);
    }

    @Test
    void customRecordsAreOwnedAndSafeResetRefusesToDeleteThem() throws Exception {
        JsonNode added = request(post("/api/tasks").content(VALID_TASK), "fakultetas", 201);
        long id = added.path("id").asLong();
        assertThat(id).isGreaterThanOrEqualTo(10000);
        request(get("/api/tasks/" + id), "klubas", 404);
        String availabilityInput = "{\"start\":\"2026-10-08T18:00\",\"end\":\"2026-10-08T19:00\"}";
        JsonNode addedAvailability = request(post("/api/availability").content(availabilityInput), "fakultetas", 201);
        long availabilityId = addedAvailability.path("id").asLong();
        assertThat(addedAvailability.path("start").asText()).isEqualTo("2026-10-08T15:00:00Z");
        request(get("/api/availability/" + availabilityId), "klubas", 404);
        request(put("/api/availability/" + availabilityId).content(availabilityInput.replace("19:00", "20:00")), "klubas", 404);
        request(delete("/api/availability/" + availabilityId), "klubas", 404);
        assertThat(request(get("/api/availability/" + availabilityId), "fakultetas", 200)).isEqualTo(addedAvailability);
        JsonNode changedAvailability = request(put("/api/availability/" + availabilityId).content(availabilityInput.replace("19:00", "20:00")), "fakultetas", 200);
        assertThat(changedAvailability.path("end").asText()).isEqualTo("2026-10-08T17:00:00Z");
        SavedSchedule beforeReset = service.schedule(FACULTY);
        request(post("/api/demo/reset"), "fakultetas", 409);
        assertThat(request(get("/api/tasks/" + id), "fakultetas", 200)).isEqualTo(added);
        assertThat(request(get("/api/availability/" + availabilityId), "fakultetas", 200)).isEqualTo(changedAvailability);
        assertThat(service.schedule(FACULTY)).isEqualTo(beforeReset);
        request(delete("/api/availability/" + availabilityId), "fakultetas", 204);
        request(get("/api/availability/" + availabilityId), "fakultetas", 404);
        request(delete("/api/tasks/" + id), "fakultetas", 204);
        request(post("/api/demo/reset"), "fakultetas", 200);
    }

    @Test
    void completedZeroWorkIsAllowedAndInvalidDatesAndPriorityAreRejected() throws Exception {
        request(put("/api/tasks/101").content(VALID_TASK.replace(":60,", ":0,").replace("false", "true")), "fakultetas", 200);
        JsonNode result = request(post("/api/plan").content(PLAN), "fakultetas", 200);
        assertThat(result.path("sessions").findValuesAsText("taskId")).containsExactly("102");
        assertThat(result.path("unscheduled").findValuesAsText("taskId")).containsExactly("102");
        request(post("/api/tasks").content(VALID_TASK.replace("HIGH", "URGENT")), "fakultetas", 400);
        request(post("/api/tasks").content(VALID_TASK.replace(":60,", ":15.9,")), "fakultetas", 400);
        request(post("/api/plan").content(PLAN.replace(":3", ":1.9")), "fakultetas", 400);
        request(post("/api/tasks").content(VALID_TASK.replace("2026-10-07T21:30+03:00", "2026-10-25T03:30")), "fakultetas", 400);
        request(post("/api/tasks").content(VALID_TASK.replace("2026-10-07T21:30+03:00", "2026-03-29T03:30")), "fakultetas", 400);
        request(post("/api/tasks").content(VALID_TASK.replace("2026-10-07T21:30+03:00", "2026-03-29T03:30+03:00")), "fakultetas", 400);
        request(post("/api/tasks").content(VALID_TASK.replace("2026-10-07T21:30+03:00", "2026-10-25T03:30+04:00")), "fakultetas", 400);
        JsonNode firstOccurrence = request(post("/api/tasks").content(VALID_TASK.replace("2026-10-07T21:30+03:00", "2026-10-25T03:30+03:00")), "fakultetas", 201);
        JsonNode secondOccurrence = request(post("/api/tasks").content(VALID_TASK.replace("2026-10-07T21:30+03:00", "2026-10-25T03:30+02:00")), "fakultetas", 201);
        assertThat(firstOccurrence.path("deadline").asText()).isEqualTo("2026-10-25T00:30:00Z");
        assertThat(secondOccurrence.path("deadline").asText()).isEqualTo("2026-10-25T01:30:00Z");
        request(post("/api/availability").content("{\"start\":\"2026-10-05T19:00\",\"end\":\"2026-10-05T18:00\"}"), "fakultetas", 400);
    }

    @Test
    void malformedJsonAndScalarTypesAreRejectedWithoutSaving() throws Exception {
        SavedSchedule before = service.schedule(FACULTY);
        Task taskBefore = service.task(FACULTY, 101);
        List<String> invalidTasks = List.of(
                VALID_TASK.replace("\"Pakeista\"", "123"),
                VALID_TASK.replace("\"Pakeista\"", "true"),
                VALID_TASK.replace("\"Pakeista\"", "12.5"),
                VALID_TASK.replace("false", "1"),
                VALID_TASK.replace("false", "\"false\""),
                VALID_TASK.replace(":60,", ":\"60\","),
                VALID_TASK + " garbage",
                VALID_TASK + " {}",
                VALID_TASK.replace("{", "{\"title\":\"Dubliuota\","));
        for (String invalid : invalidTasks) {
            JsonNode error = request(put("/api/tasks/101").content(invalid), "fakultetas", 400);
            assertThat(error.path("message").asText()).isNotBlank();
            assertThat(service.task(FACULTY, 101)).isEqualTo(taskBefore);
            assertThat(service.schedule(FACULTY)).isEqualTo(before);
        }
        request(post("/api/plan").content(PLAN.replace(":3", ":\"3\"")), "fakultetas", 400);
        request(post("/api/plan").content(PLAN + " garbage"), "fakultetas", 400);
        request(post("/api/plan").content(PLAN.replace("{", "{\"horizonDays\":1,")), "fakultetas", 400);
        assertThat(service.schedule(FACULTY)).isEqualTo(before);
    }

    @Test
    void unsupportedHttpFormatsReturnClientErrorsBeforeChangingData() throws Exception {
        SavedSchedule before = service.schedule(FACULTY);
        mvc.perform(post("/api/plan").header("X-Demo-Context", "fakultetas")
                        .contentType(MediaType.TEXT_PLAIN).content(PLAN))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Content-Type: siųskite duomenis application/json formatu."));
        mvc.perform(post("/api/plan").header("X-Demo-Context", "fakultetas")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_XML)
                        .content(PLAN.replace("18:00", "19:00")))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.message").value("Accept: API atsakymas pateikiamas application/json formatu."));
        assertThat(service.schedule(FACULTY)).isEqualTo(before);
    }

    @Test
    void timestampPrecisionNeverSilentlyMovesInputBeforeTheRequestedTime() throws Exception {
        SavedSchedule before = service.schedule(FACULTY);
        Task taskBefore = service.task(FACULTY, 101);
        List<Availability> availabilityBefore = service.availability(FACULTY);
        String nanosecondTime = "2026-10-05T18:00:00.0000001+03:00";
        JsonNode error = request(put("/api/tasks/101").content(VALID_TASK.replace("2026-10-07T21:30+03:00", nanosecondTime)), "fakultetas", 400);
        assertThat(error.path("message").asText()).contains("mikrosekundės");
        request(put("/api/availability/1001").content("{\"start\":\"" + nanosecondTime + "\",\"end\":\"2026-10-05T19:00\"}"), "fakultetas", 400);
        request(post("/api/plan").content(PLAN.replace("2026-10-05T18:00+03:00", nanosecondTime)), "fakultetas", 400);
        assertThat(service.task(FACULTY, 101)).isEqualTo(taskBefore);
        assertThat(service.availability(FACULTY)).isEqualTo(availabilityBefore);
        assertThat(service.schedule(FACULTY)).isEqualTo(before);

        JsonNode task = request(put("/api/tasks/101").content(VALID_TASK.replace("2026-10-07T21:30+03:00", "2026-10-07T21:30:00.123456+03:00")), "fakultetas", 200);
        assertThat(task.path("deadline").asText()).isEqualTo("2026-10-07T18:30:00.123456Z");
        JsonNode availability = request(put("/api/availability/1001").content("{\"start\":\"2026-10-05T18:00:00.123456\",\"end\":\"2026-10-05T21:30\"}"), "fakultetas", 200);
        assertThat(availability.path("start").asText()).isEqualTo("2026-10-05T15:00:00.123456Z");
        JsonNode result = request(post("/api/plan").content(PLAN.replace("18:00+", "18:00:00.123456+")), "fakultetas", 200);
        assertThat(result.path("planningStart").asText()).isEqualTo("2026-10-05T15:00:00.123456Z");
        assertThat(result.path("sessions").get(0).path("start").asText()).isEqualTo("2026-10-05T15:15:00Z");
    }

    @Test
    void jsonResponsesDeclareUtf8AndPreserveLithuanianText() throws Exception {
        String title = "Įžanga: užduotis ąčęėįšųūž";
        var saved = mvc.perform(put("/api/tasks/101").header("X-Demo-Context", "fakultetas")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_TASK.replace("Pakeista", title)))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(saved.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(json.readTree(saved.getContentAsString()).path("title").asText()).isEqualTo(title);

        var error = mvc.perform(put("/api/tasks/101").header("X-Demo-Context", "fakultetas")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_TASK.replace(":60,", ":-15,")))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        assertThat(error.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(json.readTree(error.getContentAsString()).path("message").asText()).contains("trukmė", "minučių");
        assertThat(service.task(FACULTY, 101).title()).isEqualTo(title);
    }

    @Test
    void sameTenantStillRequiresThePlannedUsersOwnership() throws Exception {
        DemoContext anotherUser = new DemoContext("test-only", "Testo naudotojas", 1, 12);
        repository.createContext(anotherUser);
        Task created = null;
        try {
            created = service.saveTask(anotherUser, new Task(1, "Kito naudotojo darbas",
                    Instant.parse("2026-10-08T18:30:00Z"), Priority.LOW, 60, false), true);
            request(get("/api/tasks/" + created.id()), "fakultetas", 404);
            request(put("/api/tasks/" + created.id()).content(VALID_TASK), "fakultetas", 404);
            request(delete("/api/tasks/" + created.id()), "fakultetas", 404);
            assertThat(service.task(anotherUser, created.id())).isEqualTo(created);
            assertThat(request(get("/api/tasks"), "fakultetas", 200).findValuesAsText("id")).containsExactly("101", "102");
        } finally {
            if (created != null) service.deleteTask(anotherUser, created.id());
            jdbc.update("DELETE FROM demo_context WHERE tenant_id=1 AND user_id=12");
        }
    }

    @Test
    void compositeForeignKeyRejectsSessionReferencingOtherTenant() {
        assertThatThrownBy(() -> repository.insertSession(FACULTY, new Session(201,
                Instant.parse("2026-10-07T15:00:00Z"), Instant.parse("2026-10-07T16:00:00Z"))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.schedule(FACULTY).sessions()).hasSize(4);
    }

    private JsonNode request(MockHttpServletRequestBuilder request, String context, int status) throws Exception {
        String body = mvc.perform(request.header("X-Demo-Context", context).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return body.isEmpty() ? json.nullNode() : json.readTree(body);
    }
}
