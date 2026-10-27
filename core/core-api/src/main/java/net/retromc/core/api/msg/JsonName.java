package net.retromc.core.api.msg;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Feldname im JSON, wenn der Spec-Name kein Java-Bezeichner sein kann (z. B. {@code public} in {@code PartyUpdate},
 * Spec 01 §8.2). Der Envelope-Codec in core-messaging wertet die Annotation aus.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface JsonName {

    String value();
}
