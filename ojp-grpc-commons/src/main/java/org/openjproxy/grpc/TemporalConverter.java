package org.openjproxy.grpc;

import com.google.protobuf.Timestamp;
import com.google.type.Date;
import com.google.type.TimeOfDay;
import com.openjproxy.grpc.TimestampWithZone;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Utility class for converting between Java temporal types and Protocol Buffer messages.
 * Handles conversions for:
 * - java.sql.Timestamp + ZoneId <-> TimestampWithZone (google.protobuf.Timestamp + timezone string)
 * - java.sql.Date <-> google.type.Date
 * - java.sql.Time <-> google.type.TimeOfDay
 */
@Slf4j
@UtilityClass
public class TemporalConverter {

    /**
     * Convert java.sql.Timestamp and ZoneId to TimestampWithZone proto message.
     * 
     * @param timestamp The timestamp to convert (can be null)
     * @param zoneId The timezone (must not be null if timestamp is not null)
     * @return TimestampWithZone proto message, or null if timestamp is null
     * @throws IllegalArgumentException if timestamp is not null but zoneId is null
     */
    public static TimestampWithZone toTimestampWithZone(java.sql.Timestamp timestamp, ZoneId zoneId) {
        if (timestamp == null) {
            return null;
        }
        
        if (zoneId == null) {
            throw new IllegalArgumentException(
                "ZoneId must not be null when converting timestamp. " +
                "Driver must provide ZoneId (from Calendar or ZoneId.systemDefault())"
            );
        }
        
        // Convert timestamp to Instant to get epoch seconds and nanos
        Instant instant = timestamp.toInstant();
        
        // Build google.protobuf.Timestamp
        Timestamp protoTimestamp = Timestamp.newBuilder()
            .setSeconds(instant.getEpochSecond())
            .setNanos(instant.getNano())
            .build();
        
        // Build TimestampWithZone with timezone string
        return TimestampWithZone.newBuilder()
            .setInstant(protoTimestamp)
            .setTimezone(zoneId.getId())
            .build();
    }
    
    /**
     * Convert TimestampWithZone proto message to java.sql.Timestamp.
     * The timezone is parsed and can be retrieved via getZoneIdFromTimestampWithZone.
     * 
     * @param timestampWithZone The proto message (can be null)
     * @return java.sql.Timestamp, or null if input is null
     * @throws IllegalArgumentException if timezone is missing or empty
     */
    public static java.sql.Timestamp fromTimestampWithZone(TimestampWithZone timestampWithZone) {
        if (timestampWithZone == null) {
            return null;
        }
        
        // Validate timezone is present (server-side requirement)
        String timezone = timestampWithZone.getTimezone();
        if (timezone == null || timezone.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Timezone must not be empty or missing in TimestampWithZone. " +
                "Server requires timezone to be set by the client."
            );
        }
        
        // Parse timezone to validate it (will throw DateTimeException if invalid)
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            log.debug("Failed to parse timezone '{}': {}", timezone, e.getMessage());
            throw new IllegalArgumentException("Invalid timezone string: " + timezone, e);
        }
        
        // Convert proto timestamp to Instant and then to java.sql.Timestamp
        Timestamp protoTimestamp = timestampWithZone.getInstant();
        Instant instant = Instant.ofEpochSecond(
            protoTimestamp.getSeconds(),
            protoTimestamp.getNanos()
        );
        
        // Preserve exact epoch seconds + nanos
        return java.sql.Timestamp.from(instant);
    }
    
    /**
     * Extract ZoneId from TimestampWithZone proto message.
     * 
     * @param timestampWithZone The proto message
     * @return ZoneId parsed from the timezone string
     * @throws IllegalArgumentException if timezone is missing, empty, or invalid
     */
    public static ZoneId getZoneIdFromTimestampWithZone(TimestampWithZone timestampWithZone) {
        if (timestampWithZone == null) {
            throw new IllegalArgumentException("TimestampWithZone must not be null");
        }
        
        String timezone = timestampWithZone.getTimezone();
        if (timezone == null || timezone.trim().isEmpty()) {
            throw new IllegalArgumentException("Timezone must not be empty or missing");
        }
        
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            log.debug("Failed to parse timezone '{}': {}", timezone, e.getMessage());
            throw new IllegalArgumentException("Invalid timezone string: " + timezone, e);
        }
    }
    
    /**
     * Create ZonedDateTime from TimestampWithZone for local-time semantics.
     * 
     * @param timestampWithZone The proto message
     * @return ZonedDateTime combining the instant and timezone
     */
    public static ZonedDateTime toZonedDateTime(TimestampWithZone timestampWithZone) {
        if (timestampWithZone == null) {
            return null;
        }
        
        java.sql.Timestamp timestamp = fromTimestampWithZone(timestampWithZone);
        ZoneId zoneId = getZoneIdFromTimestampWithZone(timestampWithZone);
        
        return ZonedDateTime.ofInstant(timestamp.toInstant(), zoneId);
    }
    
    /**
     * Convert java.sql.Date to google.type.Date proto message.
     * 
     * @param date The date to convert (can be null)
     * @return google.type.Date proto message, or null if date is null
     */
    public static Date toProtoDate(java.sql.Date date) {
        if (date == null) {
            return null;
        }
        
        // Convert to LocalDate to extract year, month, day
        LocalDate localDate = date.toLocalDate();
        
        return Date.newBuilder()
            .setYear(localDate.getYear())
            .setMonth(localDate.getMonthValue())
            .setDay(localDate.getDayOfMonth())
            .build();
    }
    
    /**
     * Convert google.type.Date proto message to java.sql.Date.
     * 
     * @param protoDate The proto message (can be null)
     * @return java.sql.Date, or null if input is null
     */
    public static java.sql.Date fromProtoDate(Date protoDate) {
        if (protoDate == null) {
            return null;
        }
        
        // Build LocalDate from proto fields
        LocalDate localDate = LocalDate.of(
            protoDate.getYear(),
            protoDate.getMonth(),
            protoDate.getDay()
        );
        
        return java.sql.Date.valueOf(localDate);
    }
    
    /**
     * Convert java.sql.Time to google.type.TimeOfDay proto message.
     * 
     * @param time The time to convert (can be null)
     * @return google.type.TimeOfDay proto message, or null if time is null
     */
    public static TimeOfDay toProtoTimeOfDay(Time time) {
        if (time == null) {
            return null;
        }
        
        // Convert to LocalTime to extract hours, minutes, seconds, nanos
        LocalTime localTime = time.toLocalTime();
        
        return TimeOfDay.newBuilder()
            .setHours(localTime.getHour())
            .setMinutes(localTime.getMinute())
            .setSeconds(localTime.getSecond())
            .setNanos(localTime.getNano())
            .build();
    }
    
    /**
     * Convert google.type.TimeOfDay proto message to java.sql.Time.
     * 
     * @param protoTimeOfDay The proto message (can be null)
     * @return java.sql.Time, or null if input is null
     */
    public static Time fromProtoTimeOfDay(TimeOfDay protoTimeOfDay) {
        if (protoTimeOfDay == null) {
            return null;
        }
        
        // Build LocalTime from proto fields
        LocalTime localTime = LocalTime.of(
            protoTimeOfDay.getHours(),
            protoTimeOfDay.getMinutes(),
            protoTimeOfDay.getSeconds(),
            protoTimeOfDay.getNanos()
        );
        
        return Time.valueOf(localTime);
    }
}
