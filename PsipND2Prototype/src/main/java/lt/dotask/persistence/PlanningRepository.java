package lt.dotask.persistence;

import lt.dotask.application.DemoContext;
import lt.dotask.application.RecordNotFoundException;
import lt.dotask.application.SavedSchedule;
import lt.dotask.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/** Vykdo SQL užklausas su darbo erdvės ir naudotojo filtrais; planavimo taisyklių neskaičiuoja. */
@Repository
public class PlanningRepository {
    private final JdbcTemplate jdbc;

    public PlanningRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean contextExists(DemoContext context) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM demo_context WHERE tenant_id=? AND user_id=?",
                Integer.class, context.tenantId(), context.userId()) > 0;
    }

    public void createContext(DemoContext context) {
        jdbc.update("INSERT INTO demo_context(tenant_id,user_id,name) VALUES (?,?,?)",
                context.tenantId(), context.userId(), context.name());
    }

    public void lockContext(DemoContext context) {
        // Ta pati eilutė užrakinama keičiant įvestį ir perskaičiuojant grafiką.
        jdbc.queryForObject("SELECT tenant_id FROM demo_context WHERE tenant_id=? AND user_id=? FOR UPDATE",
                Long.class, context.tenantId(), context.userId());
    }

    public List<Task> tasks(DemoContext context) {
        return jdbc.query("SELECT * FROM task WHERE tenant_id=? AND owner_user_id=? ORDER BY id",
                (row, index) -> task(row), context.tenantId(), context.userId());
    }

    public Task task(DemoContext context, long id) {
        // Vien ID neužtenka: svetimas įrašas šiame kontekste turi atrodyti kaip nerastas.
        return jdbc.query("SELECT * FROM task WHERE id=? AND tenant_id=? AND owner_user_id=?",
                (row, index) -> task(row), id, context.tenantId(), context.userId()).stream()
                .findFirst().orElseThrow(RecordNotFoundException::new);
    }

    public long createTask(DemoContext context, Task task) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO task(tenant_id,owner_user_id,title,deadline,priority,remaining_minutes,done) VALUES (?,?,?,?,?,?,?)", new String[]{"id"});
            statement.setLong(1, context.tenantId());
            statement.setLong(2, context.userId());
            statement.setString(3, task.title());
            statement.setObject(4, offset(task.deadline()));
            statement.setString(5, task.priority().name());
            statement.setInt(6, task.remainingMinutes());
            statement.setBoolean(7, task.done());
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public void insertFixtureTask(DemoContext context, Task task) {
        jdbc.update("INSERT INTO task(id,tenant_id,owner_user_id,title,deadline,priority,remaining_minutes,done) VALUES (?,?,?,?,?,?,?,?)",
                task.id(), context.tenantId(), context.userId(), task.title(), offset(task.deadline()), task.priority().name(), task.remainingMinutes(), task.done());
    }

    public void updateTask(DemoContext context, Task task) {
        requireChanged(jdbc.update("UPDATE task SET title=?,deadline=?,priority=?,remaining_minutes=?,done=? WHERE id=? AND tenant_id=? AND owner_user_id=?",
                task.title(), offset(task.deadline()), task.priority().name(), task.remainingMinutes(), task.done(), task.id(), context.tenantId(), context.userId()));
    }

    public void deleteTask(DemoContext context, long id) {
        requireChanged(jdbc.update("DELETE FROM task WHERE id=? AND tenant_id=? AND owner_user_id=?", id, context.tenantId(), context.userId()));
    }

    public List<Availability> availability(DemoContext context) {
        return jdbc.query("SELECT * FROM availability WHERE tenant_id=? AND owner_user_id=? ORDER BY starts_at,id",
                (row, index) -> availability(row), context.tenantId(), context.userId());
    }

    public Availability availability(DemoContext context, long id) {
        return jdbc.query("SELECT * FROM availability WHERE id=? AND tenant_id=? AND owner_user_id=?",
                (row, index) -> availability(row), id, context.tenantId(), context.userId()).stream()
                .findFirst().orElseThrow(RecordNotFoundException::new);
    }

    public long createAvailability(DemoContext context, Availability value) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO availability(tenant_id,owner_user_id,starts_at,ends_at) VALUES (?,?,?,?)", new String[]{"id"});
            statement.setLong(1, context.tenantId());
            statement.setLong(2, context.userId());
            statement.setObject(3, offset(value.start()));
            statement.setObject(4, offset(value.end()));
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public void insertFixtureAvailability(DemoContext context, Availability value) {
        jdbc.update("INSERT INTO availability(id,tenant_id,owner_user_id,starts_at,ends_at) VALUES (?,?,?,?,?)",
                value.id(), context.tenantId(), context.userId(), offset(value.start()), offset(value.end()));
    }

    public void updateAvailability(DemoContext context, Availability value) {
        requireChanged(jdbc.update("UPDATE availability SET starts_at=?,ends_at=? WHERE id=? AND tenant_id=? AND owner_user_id=?",
                offset(value.start()), offset(value.end()), value.id(), context.tenantId(), context.userId()));
    }

    public void deleteAvailability(DemoContext context, long id) {
        requireChanged(jdbc.update("DELETE FROM availability WHERE id=? AND tenant_id=? AND owner_user_id=?", id, context.tenantId(), context.userId()));
    }

    public void markStale(DemoContext context) {
        jdbc.update("UPDATE demo_context SET stale=TRUE WHERE tenant_id=? AND user_id=?", context.tenantId(), context.userId());
    }

    public SavedSchedule schedule(DemoContext context) {
        var sessions = jdbc.query("SELECT * FROM work_session WHERE tenant_id=? AND owner_user_id=? ORDER BY starts_at,task_id",
                (row, index) -> new Session(row.getLong("task_id"), instant(row, "starts_at"), instant(row, "ends_at")), context.tenantId(), context.userId());
        var unscheduled = jdbc.query("SELECT * FROM unscheduled_work WHERE tenant_id=? AND owner_user_id=? ORDER BY task_id",
                (row, index) -> new Unscheduled(row.getLong("task_id"), row.getInt("minutes"), row.getString("reasons").isEmpty() ? List.of() : Arrays.asList(row.getString("reasons").split("\n"))), context.tenantId(), context.userId());
        return jdbc.queryForObject("SELECT * FROM demo_context WHERE tenant_id=? AND user_id=?",
                (row, index) -> new SavedSchedule(instant(row, "planning_start"), (Integer) row.getObject("horizon_days"), row.getBoolean("stale"), sessions, unscheduled), context.tenantId(), context.userId());
    }

    /** Šalinimas ir įterpimas vykdomi kviečiančios PlanningApplicationService transakcijoje. */
    public void replaceSchedule(DemoContext context, Instant planningStart, int horizonDays, PlanResult result) {
        deleteSchedule(context);
        for (Session session : result.sessions()) {
            insertSession(context, session);
        }
        for (Unscheduled value : result.unscheduled()) {
            jdbc.update("INSERT INTO unscheduled_work(tenant_id,owner_user_id,task_id,minutes,reasons) VALUES (?,?,?,?,?)",
                    context.tenantId(), context.userId(), value.taskId(), value.minutes(), String.join("\n", value.reasons()));
        }
        jdbc.update("UPDATE demo_context SET planning_start=?,horizon_days=?,stale=FALSE WHERE tenant_id=? AND user_id=?",
                offset(planningStart), horizonDays, context.tenantId(), context.userId());
    }

    public void insertSession(DemoContext context, Session session) {
        jdbc.update("INSERT INTO work_session(tenant_id,owner_user_id,task_id,starts_at,ends_at) VALUES (?,?,?,?,?)",
                context.tenantId(), context.userId(), session.taskId(), offset(session.start()), offset(session.end()));
    }

    public void deleteSchedule(DemoContext context) {
        jdbc.update("DELETE FROM work_session WHERE tenant_id=? AND owner_user_id=?", context.tenantId(), context.userId());
        jdbc.update("DELETE FROM unscheduled_work WHERE tenant_id=? AND owner_user_id=?", context.tenantId(), context.userId());
    }

    private void requireChanged(int count) {
        if (count != 1) throw new RecordNotFoundException();
    }

    private Task task(ResultSet row) throws SQLException {
        return new Task(row.getLong("id"), row.getString("title"), instant(row, "deadline"), Priority.valueOf(row.getString("priority")), row.getInt("remaining_minutes"), row.getBoolean("done"));
    }

    private Availability availability(ResultSet row) throws SQLException {
        return new Availability(row.getLong("id"), instant(row, "starts_at"), instant(row, "ends_at"));
    }

    private Instant instant(ResultSet row, String column) throws SQLException {
        var value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime offset(Instant value) {
        // JDBC gauna aiškų UTC poslinkį, todėl nesiremiame kompiuterio numatytąja laiko juosta.
        return value.atOffset(ZoneOffset.UTC);
    }
}
