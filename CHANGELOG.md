# Changelog

All notable changes to the Open J Proxy (OJP) project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed - BREAKING
- **Replaced Java native serialization with Protocol Buffers for Map/List/Properties transport**
  - Maps, Lists, and Properties objects are now serialized using Protocol Buffers (proto3) instead of Java native serialization
  - This change provides language independence and better cross-platform compatibility
  - **Breaking Change**: Server and driver must be updated together. Old serialized data cannot be read by the new version
  - The `SerializationHandler` class is retained for JDBC-specific complex types (Blob, Clob, etc.) but is no longer used for simple containers
  - New `ProtoSerialization` utility class in `org.openjproxy.grpc.transport` package handles container serialization

### Added
- New proto file: `ojp-grpc-commons/src/main/proto/containers.proto` defining protobuf messages for Maps, Lists, and Properties
- New class: `org.openjproxy.grpc.transport.ProtoSerialization` for protobuf-based serialization of containers
- Comprehensive test suite: `ProtoSerializationTest` with 25 unit tests covering round-trip, edge cases, and error handling

### Technical Details
- Container types wrapped in a top-level `Container` message to enable type-safe deserialization
- All numbers are stored as `double` in protobuf (precision note: very large longs may lose precision)
- Nested Maps and Lists are fully supported
- UTF-8 and special characters are properly handled
- Empty containers serialize correctly (not as empty payloads)

### Migration Guide
For applications using OJP:
1. Update both server and driver to the same version simultaneously
2. No code changes required in your application - the change is transparent at the API level
3. Previously serialized data (if any was persisted) will not be readable after upgrade

For contributors/developers:
- When adding features that transport Maps, Lists, or Properties, use `ProtoSerialization` instead of `SerializationHandler`
- JDBC-specific types (Blob, Clob, etc.) should continue using Java serialization
- See `ProtoSerializationTest` for usage examples
