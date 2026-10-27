package net.retromc.core.api.util;

/**
 * Handle einer Anmeldung (Abo, Listener); {@link #close()} meldet ab und wirft keine geprüfte Ausnahme.
 * Mehrfaches Schließen ist erlaubt und wirkungslos.
 */
@FunctionalInterface
public interface Registration extends AutoCloseable {

    @Override
    void close();
}
