package lt.dotask.application;

/** Serverio nustatyta darbo erdvės ir naudotojo pora, pagal kurią atrenkami jų duomenys. */
public record DemoContext(String key, String name, long tenantId, long userId) {
}
