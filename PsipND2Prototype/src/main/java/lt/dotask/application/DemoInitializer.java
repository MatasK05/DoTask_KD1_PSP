package lt.dotask.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Po programos paleidimo paprašo sukurti tik dar neinicijuotus demo kontekstus. */
@Component
public class DemoInitializer implements ApplicationRunner {
    private final PlanningApplicationService service;

    public DemoInitializer(PlanningApplicationService service) { this.service = service; }

    @Override
    public void run(ApplicationArguments args) { service.initializeDemo(); }
}
