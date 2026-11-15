package lt.dotask.application;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Dar prieš bazės inicijavimą sustabdo testus, jeigu nurodyta ne atskira dotask_test bazė. */
public class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String url = context.getEnvironment().getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:h2:mem:dotask_test;")
                && !url.matches("jdbc:postgresql://[^/]+/dotask_test(?:\\?.*)?")) {
            throw new IllegalStateException("Testams leidžiama tik atskira dotask_test bazė; programos bazė nebus keičiama.");
        }
    }
}
