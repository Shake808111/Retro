package net.retromc.paper.core.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Blättern der {@link PagedGui} (Spec 02 §5.1 „Blättern“, §5.3). */
class PagingTest {

    private static List<Integer> numbers(int count) {
        return IntStream.rangeClosed(1, count).boxed().toList();
    }

    @Test
    void seitenzahlUndGrenzen() {
        assertEquals(1, new Pagination(0, 21).pages());
        assertEquals(1, new Pagination(21, 21).pages());
        assertEquals(2, new Pagination(22, 21).pages());
        assertEquals(3, new Pagination(43, 21).pages());

        Pagination pagination = new Pagination(43, 21);
        assertEquals(1, pagination.clamp(0));
        assertEquals(3, pagination.clamp(9));
        assertEquals(42, pagination.from(3));
        assertEquals(43, pagination.to(3));
        assertFalse(pagination.hasPrevious(1));
        assertTrue(pagination.hasNext(1));
        assertTrue(pagination.hasPrevious(3));
        assertFalse(pagination.hasNext(3));
    }

    @Test
    void ungueltigeWerte() {
        assertThrows(IllegalArgumentException.class, () -> new Pagination(-1, 21));
        assertThrows(IllegalArgumentException.class, () -> new Pagination(1, 0));
    }

    @Test
    void ersteSeiteFuelltAlleInhaltsSlots() {
        PageView<Integer> view = PageView.of(numbers(50), 1, PageLayout.CONTENT_SLOTS);

        assertEquals(1, view.page());
        assertEquals(3, view.pages());
        assertEquals(21, view.entries().size());
        assertEquals(new PageView.Entry<>(10, 1), view.entries().getFirst());
        assertEquals(new PageView.Entry<>(34, 21), view.entries().getLast());
        assertFalse(view.previous());
        assertTrue(view.next());
        assertTrue(view.indicator());
        assertFalse(view.empty());
    }

    @Test
    void letzteSeiteIstTeilweiseGefuellt() {
        PageView<Integer> view = PageView.of(numbers(50), 3, PageLayout.CONTENT_SLOTS);

        assertEquals(List.of(new PageView.Entry<>(10, 43), new PageView.Entry<>(11, 44),
                new PageView.Entry<>(12, 45), new PageView.Entry<>(13, 46), new PageView.Entry<>(14, 47),
                new PageView.Entry<>(15, 48), new PageView.Entry<>(16, 49), new PageView.Entry<>(19, 50)),
                view.entries());
        assertTrue(view.previous());
        assertFalse(view.next());
    }

    @Test
    void seiteAusserhalbWirdBegrenzt() {
        assertEquals(3, PageView.of(numbers(50), 99, PageLayout.CONTENT_SLOTS).page());
        assertEquals(1, PageView.of(numbers(50), -4, PageLayout.CONTENT_SLOTS).page());
    }

    @Test
    void eineSeiteOhneKnoepfeUndOhneAnzeige() {
        PageView<Integer> view = PageView.of(numbers(21), 1, PageLayout.CONTENT_SLOTS);

        assertEquals(1, view.pages());
        assertFalse(view.previous());
        assertFalse(view.next());
        assertFalse(view.indicator());
    }

    @Test
    void leereListe() {
        PageView<Integer> view = PageView.of(List.of(), 1, PageLayout.CONTENT_SLOTS);

        assertTrue(view.empty());
        assertEquals(1, view.page());
        assertEquals(1, view.pages());
        assertFalse(view.indicator());
    }

    @Test
    void klickSperreJeSpieler() {
        long[] now = {0};
        ClickCooldown cooldown = new ClickCooldown(ClickCooldown.DEFAULT, () -> now[0]);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(cooldown.tryClick(first));
        now[0] = 149_000_000L;
        assertFalse(cooldown.tryClick(first));
        assertTrue(cooldown.tryClick(second));
        now[0] = 150_000_000L;
        assertTrue(cooldown.tryClick(first));
        cooldown.forget(first);
        assertTrue(cooldown.tryClick(first));
    }

    @Test
    void bestaetigungZaehltGenauEinmal() {
        ConfirmState confirmed = new ConfirmState();
        assertTrue(confirmed.confirm());
        assertFalse(confirmed.cancel());
        assertFalse(confirmed.closed());
        assertEquals(ConfirmState.Outcome.CONFIRMED, confirmed.outcome().orElseThrow());

        ConfirmState escaped = new ConfirmState();
        assertTrue(escaped.closed());
        assertFalse(escaped.confirm());
        assertEquals(ConfirmState.Outcome.CANCELLED, escaped.outcome().orElseThrow());

        assertTrue(new ConfirmState().outcome().isEmpty());
    }
}
