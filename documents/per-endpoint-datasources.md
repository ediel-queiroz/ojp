# Per-Endpoint Datasource Support

## Overview

OJP now supports specifying different datasource configurations for each endpoint in a multinode URL. This allows fine-grained control over connection pool settings and other datasource-specific properties for each server in a cluster.

## URL Format

```
jdbc:ojp[endpoint1(datasource1),endpoint2(datasource2),...]_actual_jdbc_url
```

### Examples

**Same datasource for all endpoints:**
```
jdbc:ojp[localhost:10591(multinode),localhost:10592(multinode)]_postgresql://localhost:5432/defaultdb
```

**Different datasources per endpoint:**
```
jdbc:ojp[localhost:10591(default),localhost:10592(multinode)]_h2:~/test
```

**Mixed (some with, some without):**
```
jdbc:ojp[server1:1059(ds1),server2:1059,server3:1059(ds3)]_postgresql://localhost/db
```
- `server1` uses `ds1` datasource
- `server2` uses `default` datasource (no parentheses)
- `server3` uses `ds3` datasource

## Properties Configuration

In `ojp.properties`, define datasource-specific properties:

```properties
# Default datasource (used when no datasource specified)
ojp.connection.pool.maximumPoolSize=30
ojp.connection.pool.minimumIdle=2

# Multinode datasource
multinode.ojp.connection.pool.maximumPoolSize=30
multinode.ojp.connection.pool.minimumIdle=20
multinode.ojp.connection.pool.idleTimeout=2000
```

## Implementation Details

### Parsing

1. `UrlParser.parseUrlWithDataSource()` extracts datasource names from each endpoint
2. Returns `UrlParseResult` with:
   - `cleanUrl`: URL with datasource names removed
   - `dataSourceName`: First datasource (for backward compatibility)
   - `dataSourceNames`: List of all datasource names in order

### Server Endpoint Tracking

- `ServerEndpoint` class now stores datasource name
- `MultinodeUrlParser` passes datasource names when creating endpoints
- Each server endpoint knows its configured datasource

### Connection Handling

When connecting with multiple datasources:
1. Driver loads properties for the first datasource
2. If endpoints have different datasources, a warning is logged
3. Server connections are established with the loaded properties

## Current Limitations

### 1. Configuration Application

**Issue**: Only the FIRST datasource's configuration is currently used for all connections.

**Example**: With URL `jdbc:ojp[server1:1059(ds1),server2:1059(ds2)]_...`:
- Properties loaded: `ds1.ojp.connection.pool.*`
- Both servers receive the same ConnectionDetails with ds1's configuration

**Reason**: `Driver.connect()` creates a single `ConnectionDetails` object before server connections are established.

**To Fix**: Requires server-side changes:
1. Extend `ConnectionDetails` proto to support per-endpoint datasource mapping
2. Update `MultinodeConnectionManager` to pass appropriate datasource per server
3. Modify server-side pool configuration to use per-connection datasource

### 2. Mixed Datasource Behavior

**Question**: Should endpoints in the same cluster have different datasources?

**Considerations**:
- Different pool sizes could cause uneven resource usage
- Different timeout settings could cause inconsistent behavior
- May be intentional for heterogeneous clusters (e.g., read vs write servers)

**Current Behavior**: Warning logged, first datasource used for all

### 3. Backward Compatibility

**Maintained**: All existing URL formats work without changes:
- Single endpoint with datasource: `jdbc:ojp[host:port(ds)]_...`
- Single endpoint without: `jdbc:ojp[host:port]_...`
- Multiple endpoints without: `jdbc:ojp[host1:port1,host2:port2]_...`

### 4. Property Loading

`Driver.loadOjpPropertiesForDataSource()` loads properties for one datasource at a time. Supporting per-endpoint configuration requires either:
- Loading and merging multiple datasource properties
- Passing datasource names to server for server-side property loading

## Testing

### Unit Tests

`UrlParserTest.java` covers:
- Single endpoint with/without datasource
- Multiple endpoints with same datasource
- Multiple endpoints with different datasources
- Mixed (some with, some without datasources)
- Whitespace handling

### Integration Tests

Recommended tests to add:
1. Multinode connection with different datasources per endpoint
2. Verify correct datasource properties applied to each server
3. Failover behavior with mixed datasources
4. Connection pool distribution with different pool sizes

## Usage Examples

### Example 1: Testing Environment

Use different datasources for testing different configurations:

```java
String url = "jdbc:ojp[testserver1:10591(small-pool),testserver2:10592(large-pool)]_postgresql://localhost/testdb";
```

```properties
# Small pool for testserver1
small-pool.ojp.connection.pool.maximumPoolSize=5
small-pool.ojp.connection.pool.minimumIdle=1

# Large pool for testserver2
large-pool.ojp.connection.pool.maximumPoolSize=50
large-pool.ojp.connection.pool.minimumIdle=10
```

### Example 2: Production Cluster

All servers use the same datasource:

```java
String url = "jdbc:ojp[prod1:10591(production),prod2:10592(production)]_postgresql://localhost/proddb";
```

```properties
production.ojp.connection.pool.maximumPoolSize=100
production.ojp.connection.pool.minimumIdle=20
production.ojp.connection.pool.connectionTimeout=10000
```

## Next Steps

To fully support per-endpoint datasources:

1. **Protocol Enhancement**
   - Add `map<string, string> endpoint_datasources` to ConnectionDetails
   - Format: `{"host1:port1": "datasource1", "host2:port2": "datasource2"}`

2. **Server-Side Changes**
   - Update connection handlers to read endpoint_datasources
   - Apply appropriate datasource config per endpoint
   - Pool size coordination for multinode scenarios

3. **Validation**
   - Consider adding validation: warn or error on mixed datasources
   - Document best practices for datasource usage in clusters

4. **Testing**
   - Add integration tests with real multinode setup
   - Test failover with different datasources
   - Verify pool size calculations with mixed configs
