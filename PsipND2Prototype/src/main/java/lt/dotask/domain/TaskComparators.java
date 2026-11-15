package lt.dotask.domain;

import java.util.Comparator;

/** Pateikia pakeičiamas užduočių rikiavimo taisykles, kurios pačios neskirsto darbo laiko. */
public final class TaskComparators {
    private TaskComparators() { }

    public static Comparator<Task> deadlineFirst() {
        // Tik sutapus terminams svarbus prioritetas, o sutapus ir jam tvarką užbaigia ID.
        return Comparator.comparing(Task::deadline)
                .thenComparing(Comparator.comparingInt((Task task) -> task.priority().weight()).reversed())
                .thenComparingLong(Task::id);
    }

    public static Comparator<Task> priorityFirst() {
        // Alternatyva gynimo repeticijai; numatytoji programos politika lieka deadlineFirst.
        return Comparator.comparingInt((Task task) -> task.priority().weight()).reversed()
                .thenComparing(Task::deadline)
                .thenComparingLong(Task::id);
    }
}
