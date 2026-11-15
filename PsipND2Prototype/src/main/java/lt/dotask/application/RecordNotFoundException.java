package lt.dotask.application;

/** Vienoda 404 klaida ir neegzistuojančiam, ir svetimam įrašui, neatskleidžiant jo savininko. */
public class RecordNotFoundException extends RuntimeException {
    public RecordNotFoundException() {
        super("Šiame demo kontekste įrašas nerastas.");
    }
}
