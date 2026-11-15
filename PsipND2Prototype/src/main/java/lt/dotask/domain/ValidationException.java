package lt.dotask.domain;

/** Žymi netinkamą įvestį; HTTP sluoksnis jos lietuvišką paaiškinimą grąžina su 400 būsena. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
