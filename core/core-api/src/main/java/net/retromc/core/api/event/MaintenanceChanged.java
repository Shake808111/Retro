package net.retromc.core.api.event;

/** Wartung an/aus ({@code MAINTENANCE_CHANGED}, net:server-state; Spec 01 §8.2): Velocity → alle. */
public record MaintenanceChanged(boolean enabled) {
}
