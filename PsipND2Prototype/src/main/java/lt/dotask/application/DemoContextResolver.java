package lt.dotask.application;

import lt.dotask.domain.ValidationException;
import org.springframework.stereotype.Component;
import java.util.List;

/** Demo pasirinkimą susieja su žinoma savininko pora; tai nėra naudotojo autentifikacija. */
@Component
public class DemoContextResolver {
    public static final DemoContext FACULTY = new DemoContext("fakultetas", "Fakultetas", 1, 11);
    public static final DemoContext CLUB = new DemoContext("klubas", "Klubas", 2, 22);
    public static final List<DemoContext> CONTEXTS = List.of(FACULTY, CLUB);

    public DemoContext resolve(String key) {
        // Nežinomas ar trūkstamas kontekstas atmetamas, todėl nėra užklausų be savininko filtro.
        return CONTEXTS.stream().filter(context -> context.key().equals(key)).findFirst()
                .orElseThrow(() -> new ValidationException("X-Demo-Context: pasirinkite fakultetas arba klubas."));
    }
}
