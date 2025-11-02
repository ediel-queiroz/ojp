package org.openjproxy.grpc;

import com.google.protobuf.StringValue;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.RowId;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Converter utilities for language-independent serialization of UUID, URL, and RowId types
 * to/from Protocol Buffer StringValue wrappers.
 * <p>
 * These converters ensure proper handling of null vs empty string semantics:
 * - null Java value → absent/null StringValue wrapper (not present in proto)
 * - empty Java string → StringValue wrapper with empty string value
 * - present value → StringValue wrapper with encoded string value
 * </p>
 * <p>
 * Encoding rules:
 * - UUID: canonical textual representation via UUID.toString() / UUID.fromString()
 * - URL: URL.toExternalForm() / new URL(string)
 * - RowId: Base64 encoding of rowId.getBytes() - opaque bytes, vendor-specific
 * </p>
 */
public class ProtoTypeConverters {

    /**
     * Convert UUID to Protocol Buffer StringValue wrapper.
     * <p>
     * Encoding: Canonical textual representation (8-4-4-4-12 hexadecimal format)
     * Example: "550e8400-e29b-41d4-a716-446655440000"
     * </p>
     *
     * @param uuid The UUID to convert (may be null)
     * @return Optional containing StringValue with UUID string, or empty if uuid is null
     */
    public static Optional<StringValue> uuidToProto(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.of(StringValue.of(uuid.toString()));
    }

    /**
     * Convert Protocol Buffer StringValue wrapper to UUID.
     * <p>
     * Null semantics: absent wrapper → null UUID
     * Empty string is converted to UUID (though rarely meaningful)
     * </p>
     *
     * @param wrapper The StringValue wrapper (may be null)
     * @return UUID object, or null if wrapper is absent
     * @throws IllegalArgumentException if the string is not a valid UUID format
     */
    public static UUID uuidFromProto(StringValue wrapper) {
        if (wrapper == null) {
            return null;
        }
        String str = wrapper.getValue();
        if (str == null || str.isEmpty()) {
            // Empty string is technically invalid UUID, but we allow it to pass through
            // to UUID.fromString which will throw IllegalArgumentException
            return UUID.fromString(str);
        }
        return UUID.fromString(str);
    }

    /**
     * Convert URL to Protocol Buffer StringValue wrapper.
     * <p>
     * Encoding: URL.toExternalForm() - produces the string representation
     * Example: "https://example.com:8080/path?query=value#fragment"
     * </p>
     *
     * @param url The URL to convert (may be null)
     * @return Optional containing StringValue with URL string, or empty if url is null
     */
    public static Optional<StringValue> urlToProto(URL url) {
        if (url == null) {
            return Optional.empty();
        }
        return Optional.of(StringValue.of(url.toExternalForm()));
    }

    /**
     * Convert Protocol Buffer StringValue wrapper to URL.
     * <p>
     * Null semantics: absent wrapper → null URL
     * Empty string is a valid URL value (relative URL)
     * </p>
     *
     * @param wrapper The StringValue wrapper (may be null)
     * @return URL object, or null if wrapper is absent
     * @throws IllegalArgumentException if the string is not a valid URL format
     */
    public static URL urlFromProto(StringValue wrapper) {
        if (wrapper == null) {
            return null;
        }
        String str = wrapper.getValue();
        if (str == null) {
            return null;
        }
        try {
            return new URL(str);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL in proto: " + str, e);
        }
    }

    /**
     * Convert RowId to Protocol Buffer StringValue wrapper.
     * <p>
     * Encoding: Base64 encoding of rowId.getBytes()
     * The RowId bytes are vendor-specific and opaque.
     * Example: "QWJjZGVmZ2g=" (base64 of some vendor-specific bytes)
     * </p>
     * <p>
     * IMPORTANT: RowId objects are database vendor-specific and cannot be
     * reliably reconstructed from bytes alone. The bytes should be treated
     * as opaque data and passed back to the originating database.
     * </p>
     *
     * @param rowId The RowId to convert (may be null)
     * @return Optional containing StringValue with Base64-encoded bytes, or empty if rowId is null
     */
    public static Optional<StringValue> rowIdToProto(RowId rowId) {
        if (rowId == null) {
            return Optional.empty();
        }
        byte[] bytes = rowId.getBytes();
        String base64 = Base64.getEncoder().encodeToString(bytes);
        return Optional.of(StringValue.of(base64));
    }

    /**
     * Extract raw bytes from RowId Protocol Buffer StringValue wrapper.
     * <p>
     * Decodes the Base64 string back to raw bytes.
     * Null semantics: absent wrapper → null
     * Empty string → zero-length byte array
     * </p>
     * <p>
     * IMPORTANT: These bytes cannot be used to reconstruct a java.sql.RowId
     * object directly. RowId is vendor-specific and database-dependent.
     * The bytes should be treated as opaque data and used only for
     * comparison or passed back to the database that created them.
     * </p>
     *
     * @param wrapper The StringValue wrapper (may be null)
     * @return Decoded byte array, or null if wrapper is absent
     * @throws IllegalArgumentException if the string is not valid Base64
     */
    public static byte[] rowIdBytesFromProto(StringValue wrapper) {
        if (wrapper == null) {
            return null;
        }
        String str = wrapper.getValue();
        if (str == null || str.isEmpty()) {
            // Empty string means zero-length bytes
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(str);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 encoding for RowId: " + str, e);
        }
    }
}
