package net.retromc.core.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PageTest {

    private static final List<Integer> ITEMS = IntStream.rangeClosed(1, 25).boxed().toList();

    @Test
    void zehnEintraegeJeSeite() {
        Page<Integer> first = Page.of(ITEMS, 1, 10);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), first.items());
        assertEquals(3, first.pages());
        assertEquals(25, first.total());
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());
        Page<Integer> last = Page.of(ITEMS, 3, 10);
        assertEquals(List.of(21, 22, 23, 24, 25), last.items());
        assertFalse(last.hasNext());
    }

    @Test
    void seitenzahlWirdBegrenzt() {
        assertEquals(3, Page.of(ITEMS, 99, 10).page());
        assertEquals(1, Page.of(ITEMS, -2, 10).page());
        Page<Integer> empty = Page.of(List.of(), 1, 10);
        assertEquals(1, empty.pages());
        assertTrue(empty.items().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Page.of(ITEMS, 1, 0));
    }
}
