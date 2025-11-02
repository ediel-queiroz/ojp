package org.openjproxy.grpc;

import com.openjproxy.grpc.ParameterValue;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.sql.RowIdLifetime;
import java.util.GregorianCalendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for new types added to ProtoConverter: UUID, BigInteger, String[], Calendar, RowIdLifetime
 */
public class ProtoConverterNewTypesTest {

    @Test
    public void testUuidRoundTrip() {
        UUID original = UUID.randomUUID();
        ParameterValue value = ProtoConverter.toParameterValue(original);
        assertNotNull(value);
        assertEquals(ParameterValue.ValueCase.UUID_VALUE, value.getValueCase());
        
        UUID result = ProtoTypeConverters.uuidFromProto(value.getUuidValue());
        assertEquals(original, result);
    }

    @Test
    public void testBigIntegerRoundTrip() {
        BigInteger original = new BigInteger("123456789012345678901234567890");
        ParameterValue value = ProtoConverter.toParameterValue(original);
        assertNotNull(value);
        assertEquals(ParameterValue.ValueCase.BIGINTEGER_VALUE, value.getValueCase());
        
        Object result = ProtoConverter.fromParameterValue(value, null);
        assertEquals(original, result);
    }

    @Test
    public void testStringArrayRoundTrip() {
        String[] original = new String[]{"a", "b", "c", null};
        ParameterValue value = ProtoConverter.toParameterValue(original);
        assertNotNull(value);
        assertEquals(ParameterValue.ValueCase.STRING_ARRAY_VALUE, value.getValueCase());
        
        Object result = ProtoConverter.fromParameterValue(value, null);
        assertArrayEquals(original, (String[]) result);
    }

    @Test
    public void testCalendarRoundTrip() {
        GregorianCalendar original = new GregorianCalendar();
        ParameterValue value = ProtoConverter.toParameterValue(original);
        assertNotNull(value);
        // Calendar is converted to TimestampWithZone
        assertEquals(ParameterValue.ValueCase.TIMESTAMP_VALUE, value.getValueCase());
        
        // Result should be a Timestamp (Calendar is converted to Timestamp)
        Object result = ProtoConverter.fromParameterValue(value, null);
        assertNotNull(result);
        assertTrue(result instanceof java.sql.Timestamp);
    }

    @Test
    public void testRowIdLifetimeRoundTrip() {
        // Test all enum values
        for (RowIdLifetime original : RowIdLifetime.values()) {
            ParameterValue value = ProtoConverter.toParameterValue(original);
            assertNotNull(value);
            assertEquals(ParameterValue.ValueCase.ROWIDLIFETIME_VALUE, value.getValueCase());
            
            Object result = ProtoConverter.fromParameterValue(value, null);
            assertEquals(original, result);
        }
    }
}
