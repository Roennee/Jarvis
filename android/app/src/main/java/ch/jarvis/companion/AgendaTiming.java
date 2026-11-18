package ch.jarvis.companion;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class AgendaTiming {
    private static final ZoneId ZONE=ZoneId.of("Europe/Zurich");
    static String eventTime(String start,String end,boolean allDay,Instant now){
        ZonedDateTime a=Instant.parse(start).atZone(ZONE),z=Instant.parse(end).atZone(ZONE);
        LocalDate today=now.atZone(ZONE).toLocalDate();String day=a.toLocalDate().isBefore(today)?"Läuft":a.toLocalDate().equals(today)?"Heute":a.toLocalDate().equals(today.plusDays(1))?"Morgen":a.format(DateTimeFormatter.ofPattern("EEE d.M.",Locale.GERMAN));
        return day+" · "+(allDay?"Ganztägig":a.format(DateTimeFormatter.ofPattern("HH:mm"))+"–"+z.format(DateTimeFormatter.ofPattern("HH:mm")));
    }
}
