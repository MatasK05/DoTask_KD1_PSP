package lt.dotask.application;

/** Sustabdo demo atkūrimą, kai jis galėtų paliesti papildomus naudotojo įrašus; API grąžina 409. */
public class DemoResetException extends RuntimeException {
    public DemoResetException() {
        super("Atkūrimas sustabdytas: yra jūsų pridėtų įrašų. Pirmiausia pašalinkite tik tuos savo įrašus, kurių nebereikia; atkūrimas jų netrina.");
    }
}
