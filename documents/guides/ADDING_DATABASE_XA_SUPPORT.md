# Adding XA Support for New Databases

This guide explains how to add XA (distributed transaction) support for additional databases to the OJP integration test suite. The current implementation includes full PostgreSQL XA support, which serves as the reference implementation.

## Current Status

**XA Support Implemented:**
- ✅ PostgreSQL - Full XA support with test coverage

**Infrastructure Ready (Driver Detection Only):**
- Oracle, SQL Server, DB2, CockroachDB - XADataSource factory methods exist but XA testing is disabled

**Not Supported:**
- H2, MySQL, MariaDB - Limited XA support in these databases

## Prerequisites

Before adding XA support for a new database, ensure:

1. **Database XA Capability**: The database supports XA transactions with a proper XADataSource implementation
2. **JDBC Driver**: The JDBC driver with XA support is available (not required at compile-time)
3. **Privileges**: Database user has required privileges for XA operations
4. **Test Environment**: Database instance is available for integration testing

## Step-by-Step Guide

### 1. Verify XADataSource Factory Method

Check `ojp-server/src/main/java/org/openjproxy/grpc/server/xa/XADataSourceFactory.java`:

```java
public static DataSource createXADataSource(String url, String user, String password) throws SQLException {
    // ...
    if (url.contains("_postgresql:") || url.contains("_postgres:")) {
        return createPostgreSQLXADataSource(url, user, password);
    }
    // Add your database check here
    // ...
}
```

If your database doesn't have a factory method yet, add one following the PostgreSQL pattern:

```java
private static DataSource createYourDatabaseXADataSource(String url, String user, String password) 
        throws SQLException {
    // Check if driver is available
    try {
        Class.forName("com.yourdb.jdbc.xa.YourDBXADataSource");
    } catch (ClassNotFoundException e) {
        throw new SQLException("YourDB XA driver not found. Add yourdb-jdbc jar to classpath.");
    }
    
    // Parse URL to extract connection parameters
    String cleanUrl = url.replaceFirst(".*?_", "");  // Remove ojp prefix
    // Extract host, port, database from cleanUrl
    
    // Create XA DataSource using reflection (no compile-time dependency)
    try {
        Object xaDS = Class.forName("com.yourdb.jdbc.xa.YourDBXADataSource").newInstance();
        
        // Set connection properties via reflection
        xaDS.getClass().getMethod("setServerName", String.class).invoke(xaDS, host);
        xaDS.getClass().getMethod("setPortNumber", int.class).invoke(xaDS, port);
        xaDS.getClass().getMethod("setDatabaseName", String.class).invoke(xaDS, database);
        xaDS.getClass().getMethod("setUser", String.class).invoke(xaDS, user);
        xaDS.getClass().getMethod("setPassword", String.class).invoke(xaDS, password);
        
        return (DataSource) xaDS;
    } catch (Exception e) {
        throw new SQLException("Failed to create YourDB XA DataSource: " + e.getMessage(), e);
    }
}
```

### 2. Update CSV Test Files

Add XA test entries to your database's CSV file(s):

**Example: `ojp-jdbc-driver/src/test/resources/yourdb_connection.csv`**

```csv
org.openjproxy.jdbc.Driver,jdbc:ojp[localhost:1059]_yourdb://localhost:5000/testdb,user,pass,false
org.openjproxy.jdbc.Driver,jdbc:ojp[localhost:1059]_yourdb://localhost:5000/testdb,user,pass,true
```

- First line: `isXA=false` - Standard JDBC mode
- Second line: `isXA=true` - XA mode

Update all relevant CSV files that include your database.

### 3. Ensure Tests Use TestDBUtils Helper

Check that test files are using the `TestDBUtils.createConnection()` helper:

**✅ Correct Pattern:**
```java
@ParameterizedTest
@CsvFileSource(resources = "/yourdb_connection.csv")
void testOperation(String driver, String url, String user, String pwd, boolean isXA) throws Exception {
    TestDBUtils.ConnectionResult connResult = TestDBUtils.createConnection(url, user, pwd, isXA);
    Connection conn = connResult.getConnection();
    
    try {
        // Test operations
        conn.createStatement().execute("CREATE TABLE test (id INT)");
        connResult.commit();  // Handles both XA and standard JDBC
        
        // More operations...
        connResult.startXATransactionIfNeeded();  // For XA only
        conn.createStatement().execute("INSERT INTO test VALUES (1)");
        connResult.commit();
    } finally {
        connResult.close();  // Properly closes XA and JDBC connections
    }
}
```

**❌ Incorrect Pattern (ignores isXA parameter):**
```java
Connection conn = DriverManager.getConnection(url, user, pwd);  // Always JDBC mode
```

### 4. Handle Database-Specific XA Requirements

Some databases have specific requirements for XA transactions:

**Privileges:**
- Oracle requires specific GRANT statements for XA system tables
- Other databases may have similar requirements
- Document these in comments or separate setup files

**Configuration:**
- Some databases require specific connection properties for XA
- Add these to the XADataSource creation method

**Transaction Isolation:**
- Verify that the database supports XA transaction isolation levels
- Test with different isolation levels if needed

### 5. Test XA Functionality

Run tests to verify XA support:

```bash
# Run all tests for your database (both XA and JDBC modes)
mvn test -pl ojp-jdbc-driver -Dtest="YourDatabase*Test"

# Check that XA tests actually use XA transactions
# Look for log messages from XAResource.start(), commit(), end()
```

**Validation Checklist:**
- ✅ Tests run with `isXA=false` (standard JDBC)
- ✅ Tests run with `isXA=true` (XA mode)
- ✅ XA transactions are properly started (check server logs for XAResource.start())
- ✅ XA transactions are properly committed (check server logs for XAResource.commit())
- ✅ No connection leaks (check server logs for HikariCP warnings)
- ✅ Transactions are properly cleaned up on test failures
- ✅ No hanging tests

### 6. Document Database-Specific Setup

If your database requires special setup (privileges, configuration), create a setup guide:

```markdown
# YourDatabase XA Setup Guide

## Required Privileges
```sql
GRANT xa_privileges TO test_user;
```

## Common Issues
- Error XYZ: Missing privilege ABC
- Error 123: Configuration DEF required
```

## Reference: PostgreSQL Implementation

PostgreSQL serves as the reference implementation. Key files to review:

### Server-Side
**`ojp-server/src/main/java/org/openjproxy/grpc/server/xa/XADataSourceFactory.java`:**
```java
private static DataSource createPostgreSQLXADataSource(String url, String user, String password) {
    PGXADataSource xaDataSource = new PGXADataSource();
    // Parse URL and set properties
    xaDataSource.setServerNames(new String[]{host});
    xaDataSource.setPortNumbers(new int[]{port});
    xaDataSource.setDatabaseName(database);
    xaDataSource.setUser(user);
    xaDataSource.setPassword(password);
    return xaDataSource;
}
```

### Test-Side
**`ojp-jdbc-driver/src/test/resources/postgres_connection.csv`:**
```csv
org.openjproxy.jdbc.Driver,jdbc:ojp[localhost:1059]_postgresql://localhost:5432/defaultdb,testuser,testpass,false
org.openjproxy.jdbc.Driver,jdbc:ojp[localhost:1059]_postgresql://localhost:5432/defaultdb,testuser,testpass,true
```

**Test Helper Usage:**
```java
TestDBUtils.ConnectionResult connResult = TestDBUtils.createConnection(url, user, pwd, isXA);
Connection conn = connResult.getConnection();
// Operations...
connResult.commit();  // XA-aware commit
connResult.close();   // Proper cleanup
```

## Common Pitfalls

### 1. Not Using TestDBUtils Helper
**Problem:** Tests create connections with `DriverManager.getConnection()`, ignoring `isXA` parameter.
**Solution:** Always use `TestDBUtils.createConnection()` which handles XA vs JDBC branching.

### 2. Missing Privileges
**Problem:** XA operations fail with privilege errors.
**Solution:** Grant required XA privileges to database user. Document these in your setup guide.

### 3. Connection Leaks
**Problem:** XA connections not properly closed, causing resource leaks.
**Solution:** Always call `connResult.close()` in `finally` block. The helper properly ends XA transactions.

### 4. Transaction Not Started
**Problem:** After `commit()`, next operation fails because no transaction is active.
**Solution:** Call `connResult.startXATransactionIfNeeded()` after `commit()` if you need to do more work.

### 5. URL Parsing Issues
**Problem:** XADataSource factory can't parse database URL correctly.
**Solution:** Test URL parsing thoroughly. Handle different URL formats (with/without options, different separators).

## Testing Checklist

Before marking a database as "XA supported":

- [ ] XADataSource factory method created and tested
- [ ] CSV files updated with both `isXA=false` and `isXA=true` entries
- [ ] All tests using the database refactored to use `TestDBUtils.createConnection()`
- [ ] Tests pass in standard JDBC mode (`isXA=false`)
- [ ] Tests pass in XA mode (`isXA=true`)
- [ ] Server logs show XA transactions being used (XAResource.start/commit/end)
- [ ] No connection leaks in server logs
- [ ] No hanging tests
- [ ] Setup requirements documented (if any)
- [ ] Privilege requirements documented (if any)
- [ ] Common errors documented with solutions

## Getting Help

- Review PostgreSQL implementation as reference
- Check `TestDBUtils.java` for XA transaction lifecycle management
- Review `XADataSourceFactory.java` for DataSource creation patterns
- Test incrementally - get JDBC mode working first, then add XA
