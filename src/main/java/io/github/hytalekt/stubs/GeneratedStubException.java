package io.github.hytalekt.stubs;

public class GeneratedStubException extends RuntimeException {
    public GeneratedStubException() {
        super("Attempted to use a stub! Make sure hytale-stubs isn't shaded and is only being used as a reference implementation!");
    }
}
