package lt.dotask;

import lt.dotask.domain.SchedulingService;
import lt.dotask.domain.TaskComparators;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Paleidžia Spring Boot programą ir sujungia jos dalis. */
@SpringBootApplication
public class DoTaskApplication {
    public static void main(String[] args) {
        SpringApplication.run(DoTaskApplication.class, args);
    }

    @Bean
    SchedulingService schedulingService() {
        // Čia parenkama visos programos rikiavimo politika, nekeičiant paties algoritmo.
        return new SchedulingService(TaskComparators.deadlineFirst());
    }
}
