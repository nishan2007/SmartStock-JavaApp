package services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Shared deterministic JSON codec for the LAN contract, including java.time values. */
public final class LanJson {
    private LanJson() {}
    public static Gson create(){
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class,(JsonSerializer<Instant>)(v,t,c)->c.serialize(v.toString()))
                .registerTypeAdapter(Instant.class,(JsonDeserializer<Instant>)(v,t,c)->Instant.parse(v.getAsString()))
                .registerTypeAdapter(LocalDate.class,(JsonSerializer<LocalDate>)(v,t,c)->c.serialize(v.toString()))
                .registerTypeAdapter(LocalDate.class,(JsonDeserializer<LocalDate>)(v,t,c)->LocalDate.parse(v.getAsString()))
                .registerTypeAdapter(LocalDateTime.class,(JsonSerializer<LocalDateTime>)(v,t,c)->c.serialize(v.toString()))
                .registerTypeAdapter(LocalDateTime.class,(JsonDeserializer<LocalDateTime>)(v,t,c)->LocalDateTime.parse(v.getAsString()))
                .registerTypeAdapter(LocalTime.class,(JsonSerializer<LocalTime>)(v,t,c)->c.serialize(v.toString()))
                .registerTypeAdapter(LocalTime.class,(JsonDeserializer<LocalTime>)(v,t,c)->LocalTime.parse(v.getAsString()))
                // ZoneId.of(...) returns an internal ZoneRegion subclass. A normal type
                // adapter only matches ZoneId exactly, so Gson otherwise reflects into
                // java.time.ZoneRegion and fails under the Java module access rules.
                .registerTypeHierarchyAdapter(ZoneId.class,(JsonSerializer<ZoneId>)(v,t,c)->c.serialize(v.getId()))
                .registerTypeHierarchyAdapter(ZoneId.class,(JsonDeserializer<ZoneId>)(v,t,c)->ZoneId.of(v.getAsString()))
                .registerTypeAdapter(ZonedDateTime.class,(JsonSerializer<ZonedDateTime>)(v,t,c)->c.serialize(v.toString()))
                .registerTypeAdapter(ZonedDateTime.class,(JsonDeserializer<ZonedDateTime>)(v,t,c)->ZonedDateTime.parse(v.getAsString()))
                .enableComplexMapKeySerialization().create();
    }
}
