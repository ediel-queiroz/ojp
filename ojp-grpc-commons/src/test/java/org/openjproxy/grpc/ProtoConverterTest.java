package org.openjproxy.grpc;

import com.openjproxy.grpc.ParameterValue;
import org.junit.jupiter.api.Test;
import org.openjproxy.grpc.dto.ParameterType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProtoConverter to verify correct handling of null vs empty byte arrays.
 */
public class ProtoConverterTest {

    @Test
    void testNullValueSerialization() {
        // Convert null to ParameterValue
        ParameterValue pv = ProtoConverter.toParameterValue(null);
        
        // Verify is_null is set
        assertEquals(ParameterValue.ValueCase.IS_NULL, pv.getValueCase());
        assertTrue(pv.getIsNull());
        
        // Convert back to Object
        Object result = ProtoConverter.fromParameterValue(pv, ParameterType.BYTES);
        assertNull(result);
    }

    @Test
    void testEmptyByteArraySerialization() {
        // Convert empty byte array to ParameterValue
        byte[] emptyArray = new byte[0];
        ParameterValue pv = ProtoConverter.toParameterValue(emptyArray);
        
        // Verify bytes_value is set (not is_null)
        assertEquals(ParameterValue.ValueCase.BYTES_VALUE, pv.getValueCase());
        assertNotNull(pv.getBytesValue());
        assertEquals(0, pv.getBytesValue().size());
        
        // Convert back to Object with BYTES type (should preserve empty array)
        Object result = ProtoConverter.fromParameterValue(pv, ParameterType.BYTES);
        assertNotNull(result, "Empty byte array should not be null");
        assertTrue(result instanceof byte[]);
        assertEquals(0, ((byte[]) result).length);
    }

    @Test
    void testEmptyByteArrayWithBlobType() {
        // Convert empty byte array to ParameterValue
        byte[] emptyArray = new byte[0];
        ParameterValue pv = ProtoConverter.toParameterValue(emptyArray);
        
        // Convert back with BLOB type
        Object result = ProtoConverter.fromParameterValue(pv, ParameterType.BLOB);
        assertNotNull(result, "Empty BLOB should not be null");
        assertTrue(result instanceof byte[]);
        assertEquals(0, ((byte[]) result).length);
    }

    @Test
    void testNonEmptyByteArraySerialization() {
        // Convert non-empty byte array to ParameterValue
        byte[] dataArray = new byte[]{1, 2, 3, 4, 5};
        ParameterValue pv = ProtoConverter.toParameterValue(dataArray);
        
        // Verify bytes_value is set
        assertEquals(ParameterValue.ValueCase.BYTES_VALUE, pv.getValueCase());
        assertEquals(5, pv.getBytesValue().size());
        
        // Convert back to Object
        Object result = ProtoConverter.fromParameterValue(pv, ParameterType.BYTES);
        assertNotNull(result);
        assertTrue(result instanceof byte[]);
        assertArrayEquals(dataArray, (byte[]) result);
    }

    @Test
    void testNullVsEmptyByteArrayDistinction() {
        // Test null
        ParameterValue nullPv = ProtoConverter.toParameterValue(null);
        Object nullResult = ProtoConverter.fromParameterValue(nullPv, ParameterType.BYTES);
        assertNull(nullResult, "Null should remain null");
        
        // Test empty byte array
        byte[] emptyArray = new byte[0];
        ParameterValue emptyPv = ProtoConverter.toParameterValue(emptyArray);
        Object emptyResult = ProtoConverter.fromParameterValue(emptyPv, ParameterType.BYTES);
        assertNotNull(emptyResult, "Empty byte array should not be null");
        assertTrue(emptyResult instanceof byte[]);
        assertEquals(0, ((byte[]) emptyResult).length);
        
        // Verify they're different
        assertNotEquals(nullPv.getValueCase(), emptyPv.getValueCase());
    }

    @Test
    void testStringValueSerialization() {
        String testString = "test string";
        ParameterValue pv = ProtoConverter.toParameterValue(testString);
        
        assertEquals(ParameterValue.ValueCase.STRING_VALUE, pv.getValueCase());
        
        Object result = ProtoConverter.fromParameterValue(pv, ParameterType.STRING);
        assertEquals(testString, result);
    }

    @Test
    void testIntegerValueSerialization() {
        Integer testInt = 42;
        ParameterValue pv = ProtoConverter.toParameterValue(testInt);
        
        assertEquals(ParameterValue.ValueCase.INT_VALUE, pv.getValueCase());
        
        Object result = ProtoConverter.fromParameterValue(pv, ParameterType.INT);
        assertEquals(testInt, result);
    }

    @Test
    void testBackwardCompatibilityForNonBinaryTypes() {
        // For non-binary types (like OBJECT), empty bytes should be handled gracefully
        // This tests that our change doesn't break existing behavior
        ParameterValue emptyBytesPv = ParameterValue.newBuilder()
                .setBytesValue(com.google.protobuf.ByteString.EMPTY)
                .build();
        
        // For OBJECT type, empty bytes should be handled gracefully (no crash)
        // Deserialization may fail for empty bytes, in which case raw bytes are returned
        Object result = ProtoConverter.fromParameterValue(emptyBytesPv, ParameterType.OBJECT);
        
        // If deserialization fails, the result will be the raw byte array
        if (result instanceof byte[]) {
            assertEquals(0, ((byte[]) result).length, "Empty bytes should remain empty");
        }
    }
}
