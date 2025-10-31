package org.openjproxy.grpc;

import com.google.protobuf.ByteString;
import com.openjproxy.grpc.IntArray;
import com.openjproxy.grpc.LongArray;
import com.openjproxy.grpc.OpQueryResultProto;
import com.openjproxy.grpc.ParameterProto;
import com.openjproxy.grpc.ParameterTypeProto;
import com.openjproxy.grpc.ParameterValue;
import com.openjproxy.grpc.PropertyEntry;
import com.openjproxy.grpc.ResultRow;
import org.openjproxy.grpc.dto.OpQueryResult;
import org.openjproxy.grpc.dto.Parameter;
import org.openjproxy.grpc.dto.ParameterType;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;

/**
 * Converter between Java DTOs and Protocol Buffer messages.
 */
public class ProtoConverter {

    /**
     * Convert a Parameter DTO to ParameterProto message.
     */
    public static ParameterProto toProto(Parameter parameter) {
        if (parameter == null) {
            return null;
        }

        ParameterProto.Builder builder = ParameterProto.newBuilder()
                .setIndex(parameter.getIndex() != null ? parameter.getIndex() : 0)
                .setType(toProto(parameter.getType()));

        if (parameter.getValues() != null) {
            for (Object value : parameter.getValues()) {
                builder.addValues(toParameterValue(value));
            }
        }

        return builder.build();
    }

    /**
     * Convert a ParameterProto message to Parameter DTO.
     */
    public static Parameter fromProto(ParameterProto proto) {
        if (proto == null) {
            return null;
        }

        ParameterType type = fromProto(proto.getType());
        List<Object> values = new ArrayList<>();
        for (ParameterValue pv : proto.getValuesList()) {
            Object value = fromParameterValue(pv, type);
            values.add(value);
        }

        return Parameter.builder()
                .index(proto.getIndex())
                .type(type)
                .values(values)
                .build();
    }

    /**
     * Convert a list of Parameter DTOs to a list of ParameterProto messages.
     */
    public static List<ParameterProto> toProtoList(List<Parameter> parameters) {
        if (parameters == null) {
            return new ArrayList<>();
        }

        List<ParameterProto> result = new ArrayList<>();
        for (Parameter param : parameters) {
            result.add(toProto(param));
        }
        return result;
    }

    /**
     * Convert a list of ParameterProto messages to a list of Parameter DTOs.
     */
    public static List<Parameter> fromProtoList(List<ParameterProto> protos) {
        if (protos == null) {
            return new ArrayList<>();
        }

        List<Parameter> result = new ArrayList<>();
        for (ParameterProto proto : protos) {
            result.add(fromProto(proto));
        }
        return result;
    }

    /**
     * Convert ParameterType enum to ParameterTypeProto.
     */
    public static ParameterTypeProto toProto(ParameterType type) {
        if (type == null) {
            return ParameterTypeProto.PT_NULL;
        }

        switch (type) {
            case NULL: return ParameterTypeProto.PT_NULL;
            case BOOLEAN: return ParameterTypeProto.PT_BOOLEAN;
            case BYTE: return ParameterTypeProto.PT_BYTE;
            case SHORT: return ParameterTypeProto.PT_SHORT;
            case INT: return ParameterTypeProto.PT_INT;
            case LONG: return ParameterTypeProto.PT_LONG;
            case FLOAT: return ParameterTypeProto.PT_FLOAT;
            case DOUBLE: return ParameterTypeProto.PT_DOUBLE;
            case BIG_DECIMAL: return ParameterTypeProto.PT_BIG_DECIMAL;
            case STRING: return ParameterTypeProto.PT_STRING;
            case BYTES: return ParameterTypeProto.PT_BYTES;
            case DATE: return ParameterTypeProto.PT_DATE;
            case TIME: return ParameterTypeProto.PT_TIME;
            case TIMESTAMP: return ParameterTypeProto.PT_TIMESTAMP;
            case ASCII_STREAM: return ParameterTypeProto.PT_ASCII_STREAM;
            case UNICODE_STREAM: return ParameterTypeProto.PT_UNICODE_STREAM;
            case BINARY_STREAM: return ParameterTypeProto.PT_BINARY_STREAM;
            case OBJECT: return ParameterTypeProto.PT_OBJECT;
            case CHARACTER_READER: return ParameterTypeProto.PT_CHARACTER_READER;
            case REF: return ParameterTypeProto.PT_REF;
            case BLOB: return ParameterTypeProto.PT_BLOB;
            case CLOB: return ParameterTypeProto.PT_CLOB;
            case ARRAY: return ParameterTypeProto.PT_ARRAY;
            case URL: return ParameterTypeProto.PT_URL;
            case ROW_ID: return ParameterTypeProto.PT_ROW_ID;
            case N_STRING: return ParameterTypeProto.PT_N_STRING;
            case N_CHARACTER_STREAM: return ParameterTypeProto.PT_N_CHARACTER_STREAM;
            case N_CLOB: return ParameterTypeProto.PT_N_CLOB;
            case SQL_XML: return ParameterTypeProto.PT_SQL_XML;
            default: return ParameterTypeProto.PT_OBJECT;
        }
    }

    /**
     * Convert ParameterTypeProto to ParameterType enum.
     */
    public static ParameterType fromProto(ParameterTypeProto proto) {
        if (proto == null) {
            return ParameterType.NULL;
        }

        switch (proto) {
            case PT_NULL: return ParameterType.NULL;
            case PT_BOOLEAN: return ParameterType.BOOLEAN;
            case PT_BYTE: return ParameterType.BYTE;
            case PT_SHORT: return ParameterType.SHORT;
            case PT_INT: return ParameterType.INT;
            case PT_LONG: return ParameterType.LONG;
            case PT_FLOAT: return ParameterType.FLOAT;
            case PT_DOUBLE: return ParameterType.DOUBLE;
            case PT_BIG_DECIMAL: return ParameterType.BIG_DECIMAL;
            case PT_STRING: return ParameterType.STRING;
            case PT_BYTES: return ParameterType.BYTES;
            case PT_DATE: return ParameterType.DATE;
            case PT_TIME: return ParameterType.TIME;
            case PT_TIMESTAMP: return ParameterType.TIMESTAMP;
            case PT_ASCII_STREAM: return ParameterType.ASCII_STREAM;
            case PT_UNICODE_STREAM: return ParameterType.UNICODE_STREAM;
            case PT_BINARY_STREAM: return ParameterType.BINARY_STREAM;
            case PT_OBJECT: return ParameterType.OBJECT;
            case PT_CHARACTER_READER: return ParameterType.CHARACTER_READER;
            case PT_REF: return ParameterType.REF;
            case PT_BLOB: return ParameterType.BLOB;
            case PT_CLOB: return ParameterType.CLOB;
            case PT_ARRAY: return ParameterType.ARRAY;
            case PT_URL: return ParameterType.URL;
            case PT_ROW_ID: return ParameterType.ROW_ID;
            case PT_N_STRING: return ParameterType.N_STRING;
            case PT_N_CHARACTER_STREAM: return ParameterType.N_CHARACTER_STREAM;
            case PT_N_CLOB: return ParameterType.N_CLOB;
            case PT_SQL_XML: return ParameterType.SQL_XML;
            default: return ParameterType.OBJECT;
        }
    }

    /**
     * Convert a Java object to ParameterValue.
     * Uses Java serialization for complex types to preserve exact type information.
     */
    public static ParameterValue toParameterValue(Object value) {
        ParameterValue.Builder builder = ParameterValue.newBuilder();

        if (value == null) {
            // Return empty ParameterValue for null
            return builder.build();
        } else if (value instanceof Boolean) {
            builder.setBoolValue((Boolean) value);
        } else if (value instanceof Byte) {
            builder.setIntValue((Byte) value);
        } else if (value instanceof Short) {
            builder.setIntValue((Short) value);
        } else if (value instanceof Integer) {
            builder.setIntValue((Integer) value);
        } else if (value instanceof Long) {
            builder.setLongValue((Long) value);
        } else if (value instanceof Float) {
            builder.setFloatValue((Float) value);
        } else if (value instanceof Double) {
            builder.setDoubleValue((Double) value);
        } else if (value instanceof String) {
            builder.setStringValue((String) value);
        } else if (value instanceof byte[]) {
            builder.setBytesValue(ByteString.copyFrom((byte[]) value));
        } else if (value instanceof int[]) {
            // Handle int array
            int[] arr = (int[]) value;
            IntArray.Builder intArrayBuilder = IntArray.newBuilder();
            for (int i : arr) {
                intArrayBuilder.addValues(i);
            }
            builder.setIntArrayValue(intArrayBuilder.build());
        } else if (value instanceof long[]) {
            // Handle long array
            long[] arr = (long[]) value;
            LongArray.Builder longArrayBuilder = LongArray.newBuilder();
            for (long l : arr) {
                longArrayBuilder.addValues(l);
            }
            builder.setLongArrayValue(longArrayBuilder.build());
        } else {
            // For all other complex types (BigDecimal, Date, Time, Timestamp, UUID, Map, etc.),
            // use Java serialization to preserve exact type information.
            // This is necessary because converting them to primitives (String, Long) loses type info.
            builder.setBytesValue(ByteString.copyFrom(SerializationHandler.serialize(value)));
        }

        return builder.build();
    }

    /**
     * Convert ParameterValue to Java object.
     * Note: This returns a generic Object, caller needs to handle type casting.
     * 
     * @param value The ParameterValue to convert
     * @param type The ParameterType to help determine how to handle bytes
     */
    public static Object fromParameterValue(ParameterValue value, ParameterType type) {
        if (value == null) {
            return null;
        }

        switch (value.getValueCase()) {
            case BOOL_VALUE:
                return value.getBoolValue();
            case INT_VALUE:
                return value.getIntValue();
            case LONG_VALUE:
                return value.getLongValue();
            case FLOAT_VALUE:
                return value.getFloatValue();
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case STRING_VALUE:
                return value.getStringValue();
            case BYTES_VALUE:
                byte[] bytes = value.getBytesValue().toByteArray();
                if (bytes.length == 0) {
                    return null;
                }
                
                // Determine if bytes should be deserialized based on ParameterType
                if (type != null && !shouldDeserializeBytes(type)) {
                    // Binary data types (BYTES, BLOB, BINARY_STREAM) - return raw bytes
                    return bytes;
                } else if (type != null) {
                    // Complex types (OBJECT, ARRAY, etc.) with known type - deserialize
                    return SerializationHandler.deserialize(bytes, Object.class);
                } else {
                    // Unknown type (result set data) - try to deserialize, but return raw bytes if it fails
                    // This handles both serialized complex types (BigDecimal, Date) and raw binary data (BLOBs)
                    try {
                        return SerializationHandler.deserialize(bytes, Object.class);
                    } catch (RuntimeException e) {
                        // If deserialization fails (e.g., StreamCorruptedException for BLOB data),
                        // return raw bytes
                        return bytes;
                    }
                }
            case INT_ARRAY_VALUE:
                // Convert IntArray proto message to int[]
                IntArray intArray = value.getIntArrayValue();
                int[] intArr = new int[intArray.getValuesCount()];
                for (int i = 0; i < intArray.getValuesCount(); i++) {
                    intArr[i] = intArray.getValues(i);
                }
                return intArr;
            case LONG_ARRAY_VALUE:
                // Convert LongArray proto message to long[]
                LongArray longArray = value.getLongArrayValue();
                long[] longArr = new long[longArray.getValuesCount()];
                for (int i = 0; i < longArray.getValuesCount(); i++) {
                    longArr[i] = longArray.getValues(i);
                }
                return longArr;
            case VALUE_NOT_SET:
            default:
                return null;
        }
    }
    
    /**
     * Determine if bytes should be deserialized based on ParameterType.
     * Only deserialize for OBJECT and complex types that were serialized.
     * For binary data types (BYTES, BLOB, BINARY_STREAM), return raw bytes.
     */
    private static boolean shouldDeserializeBytes(ParameterType type) {
        // If no type information, try to deserialize (for CallResourceResponse compatibility)
        // This may fail for raw binary data, but caller should handle that
        if (type == null) {
            return true;
        }
        
        switch (type) {
            case BYTES:
            case BINARY_STREAM:
            case BLOB:
            case ASCII_STREAM:
            case UNICODE_STREAM:
            case CHARACTER_READER:
            case N_CHARACTER_STREAM:
                // These are raw binary/text data - don't deserialize
                return false;
            case OBJECT:
            case ARRAY:
            case REF:
            case CLOB:
            case N_CLOB:
            case SQL_XML:
                // These are complex objects that were serialized
                return true;
            default:
                // For other types, assume no deserialization needed
                return false;
        }
    }
    
    /**
     * Convert ParameterValue to Java object without type information.
     * This assumes bytes should be deserialized (for backward compatibility with CallResourceResponse).
     * Use fromParameterValue(ParameterValue, ParameterType) when type information is available.
     */
    public static Object fromParameterValue(ParameterValue value) {
        // For calls without type info, try to deserialize bytes
        // This is mainly for CallResourceResponse values where we don't have ParameterType
        return fromParameterValue(value, null);
    }

    /**
     * Convert OpQueryResult DTO to OpQueryResultProto message.
     */
    public static OpQueryResultProto toProto(OpQueryResult result) {
        if (result == null) {
            return null;
        }

        OpQueryResultProto.Builder builder = OpQueryResultProto.newBuilder()
                .setResultSetUUID(result.getResultSetUUID() != null ? result.getResultSetUUID() : "");

        if (result.getLabels() != null) {
            builder.addAllLabels(result.getLabels());
        }

        if (result.getRows() != null) {
            for (Object[] row : result.getRows()) {
                ResultRow.Builder rowBuilder = ResultRow.newBuilder();
                if (row != null) {
                    for (Object col : row) {
                        rowBuilder.addColumns(toParameterValue(col));
                    }
                }
                builder.addRows(rowBuilder.build());
            }
        }

        return builder.build();
    }

    /**
     * Convert OpQueryResultProto message to OpQueryResult DTO.
     */
    public static OpQueryResult fromProto(OpQueryResultProto proto) {
        if (proto == null) {
            return null;
        }

        List<Object[]> rows = new ArrayList<>();
        for (ResultRow row : proto.getRowsList()) {
            Object[] rowData = new Object[row.getColumnsCount()];
            for (int i = 0; i < row.getColumnsCount(); i++) {
                rowData[i] = fromParameterValue(row.getColumns(i));
            }
            rows.add(rowData);
        }

        return OpQueryResult.builder()
                .resultSetUUID(proto.getResultSetUUID())
                .labels(new ArrayList<>(proto.getLabelsList()))
                .rows(rows)
                .build();
    }

    /**
     * Convert a Map of properties to a list of PropertyEntry messages.
     */
    public static List<PropertyEntry> propertiesToProto(Map<String, Object> properties) {
        if (properties == null) {
            return new ArrayList<>();
        }

        List<PropertyEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            PropertyEntry.Builder builder = PropertyEntry.newBuilder()
                    .setKey(entry.getKey());

            Object value = entry.getValue();
            if (value == null) {
                // Empty builder for null values
            } else if (value instanceof Boolean) {
                builder.setBoolValue((Boolean) value);
            } else if (value instanceof Byte) {
                builder.setIntValue((Byte) value);
            } else if (value instanceof Short) {
                builder.setIntValue((Short) value);
            } else if (value instanceof Integer) {
                builder.setIntValue((Integer) value);
            } else if (value instanceof Long) {
                builder.setLongValue((Long) value);
            } else if (value instanceof Float) {
                builder.setFloatValue((Float) value);
            } else if (value instanceof Double) {
                builder.setDoubleValue((Double) value);
            } else if (value instanceof String) {
                builder.setStringValue((String) value);
            } else if (value instanceof byte[]) {
                builder.setBytesValue(ByteString.copyFrom((byte[]) value));
            } else {
                // For complex objects, serialize as bytes
                builder.setBytesValue(ByteString.copyFrom(SerializationHandler.serialize(value)));
            }

            entries.add(builder.build());
        }

        return entries;
    }

    /**
     * Convert a list of PropertyEntry messages to a Map of properties.
     */
    public static Map<String, Object> propertiesFromProto(List<PropertyEntry> entries) {
        if (entries == null) {
            return new HashMap<>();
        }

        Map<String, Object> properties = new HashMap<>();
        for (PropertyEntry entry : entries) {
            Object value = null;
            switch (entry.getValueCase()) {
                case BOOL_VALUE:
                    value = entry.getBoolValue();
                    break;
                case INT_VALUE:
                    value = entry.getIntValue();
                    break;
                case LONG_VALUE:
                    value = entry.getLongValue();
                    break;
                case FLOAT_VALUE:
                    value = entry.getFloatValue();
                    break;
                case DOUBLE_VALUE:
                    value = entry.getDoubleValue();
                    break;
                case STRING_VALUE:
                    value = entry.getStringValue();
                    break;
                case BYTES_VALUE:
                    // Try to deserialize as Object first
                    byte[] bytes = entry.getBytesValue().toByteArray();
                    try {
                        value = SerializationHandler.deserialize(bytes, Object.class);
                    } catch (Exception e) {
                        // If deserialization fails, keep as byte array
                        value = bytes;
                    }
                    break;
                case VALUE_NOT_SET:
                default:
                    value = null;
                    break;
            }
            properties.put(entry.getKey(), value);
        }

        return properties;
    }

    /**
     * Convert a list of objects to a list of ParameterValue messages.
     */
    public static List<ParameterValue> objectListToParameterValues(List<Object> objects) {
        if (objects == null) {
            return new ArrayList<>();
        }

        List<ParameterValue> values = new ArrayList<>();
        for (Object obj : objects) {
            values.add(toParameterValue(obj));
        }
        return values;
    }

    /**
     * Convert a list of ParameterValue messages to a list of objects.
     */
    public static List<Object> parameterValuesToObjectList(List<ParameterValue> values) {
        if (values == null) {
            return new ArrayList<>();
        }

        List<Object> objects = new ArrayList<>();
        for (ParameterValue value : values) {
            objects.add(fromParameterValue(value));
        }
        return objects;
    }
}
