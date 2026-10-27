/**
 * Statistik-Logik ohne Plattform-Abhängigkeit (Spec 02 §1): Punkteformeln aus {@code stats.mode.points_formula},
 * abgeleitete Werte (K/D, Siegesquote, Spec 02 §6.8), rollierendes 30-Tage-Fenster (§6.5), Ranking-Score und
 * Tie-Break (§6.6) sowie die Ausgabe-Bausteine für {@code /stats}. Nutzbar von paper-core und {@code :services:api}.
 */
@NullMarked
package net.retromc.core.stats;

import org.jspecify.annotations.NullMarked;
