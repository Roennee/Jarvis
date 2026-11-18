package ch.jarvis.companion;

import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class AgendaTimingTest {
    @Test public void zurichMidnightCountsAsTomorrow(){
        assertEquals("Morgen · 00:30–01:30",AgendaTiming.eventTime("2026-11-18T23:30:00Z","2026-11-19T00:30:00Z",false,Instant.parse("2026-11-18T20:00:00Z")));
    }
    @Test public void ongoingAllDayDoesNotPretendItStartsToday(){
        assertEquals("Läuft · Ganztägig",AgendaTiming.eventTime("2026-11-15T23:00:00Z","2026-11-19T23:00:00Z",true,Instant.parse("2026-11-18T12:00:00Z")));
    }
    @Test public void summerClockChangeUsesZurichOffsets(){
        assertEquals("Heute · 01:30–03:30",AgendaTiming.eventTime("2026-03-29T00:30:00Z","2026-03-29T01:30:00Z",false,Instant.parse("2026-03-29T00:00:00Z")));
    }
}
