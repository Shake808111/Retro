package net.retromc.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class MigrationsTest {

    @Test
    void migrationLiegtImKlassenpfad() {
        assertNotNull(getClass().getClassLoader().getResource("migrations/V1__init.sql"),
                "db/migrations/V1__init.sql muss als Ressource migrations/V1__init.sql verfügbar sein");
    }
}
