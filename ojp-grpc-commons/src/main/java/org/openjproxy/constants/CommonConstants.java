package org.openjproxy.constants;

/**
 * Holds common constants used in both the JDBC driver and the OJP proxy server.
 */
public class CommonConstants {
    public static final int ROWS_PER_RESULT_SET_DATA_BLOCK = 100;
    public static final int MAX_LOB_DATA_BLOCK_SIZE = 1024;//1KB per block
    public static final int PREPARED_STATEMENT_BINARY_STREAM_INDEX = 1;
    public static final int PREPARED_STATEMENT_BINARY_STREAM_LENGTH = 2;
    public static final int PREPARED_STATEMENT_BINARY_STREAM_SQL = 3;
    public static final int PREPARED_STATEMENT_UUID_BINARY_STREAM = 4;
    public static final String PREPARED_STATEMENT_SQL_KEY = "PREPARED_STATEMENT_SQL_KEY";
    public static final String PREPARED_STATEMENT_ADD_BATCH_FLAG = "PREPARED_STATEMENT_ADD_BATCH_FLAG";
    public static final String PREPARED_STATEMENT_EXECUTE_BATCH_FLAG = "PREPARED_STATEMENT_EXECUTE_BATCH_FLAG";
    public static final String STATEMENT_RESULT_SET_TYPE_KEY = "STATEMENT_RESULT_SET_TYPE_KEY";
    public static final String STATEMENT_RESULT_SET_CONCURRENCY_KEY = "STATEMENT_RESULT_SET_CONCURRENCY_KEY";
    public static final String STATEMENT_RESULT_SET_HOLDABILITY_KEY = "STATEMENT_RESULT_SET_HOLDABILITY_KEY";
    public static final String STATEMENT_AUTO_GENERATED_KEYS_KEY = "STATEMENT_AUTO_GENERATED_KEYS_KEY";
    public static final String STATEMENT_COLUMN_INDEXES_KEY = "STATEMENT_COLUMN_INDEXES_KEY";
    public static final String STATEMENT_COLUMN_NAMES_KEY = "STATEMENT_COLUMN_NAMES_KEY";
    public static final String RESULT_SET_ROW_BY_ROW_MODE = "RESULT_SET_ROW_BY_ROW_MODE";
    public static final int DEFAULT_PORT_NUMBER = 1059;
    public static final String OJP_REGEX_PATTERN = "ojp\\[([^\\]]+)\\]";
    public static final String OJP_CLOB_PREFIX = "OJP_CLOB_PREFIX:";

    // Configuration property keys
    public static final String DATASOURCE_NAME_PROPERTY = "ojp.datasource.name";
    public static final String MAXIMUM_POOL_SIZE_PROPERTY = "ojp.connection.pool.maximumPoolSize";
    public static final String MINIMUM_IDLE_PROPERTY = "ojp.connection.pool.minimumIdle";
    public static final String IDLE_TIMEOUT_PROPERTY = "ojp.connection.pool.idleTimeout";
    public static final String MAX_LIFETIME_PROPERTY = "ojp.connection.pool.maxLifetime";
    public static final String CONNECTION_TIMEOUT_PROPERTY = "ojp.connection.pool.connectionTimeout";
    public static final String MAX_XA_TRANSACTIONS_PROPERTY = "ojp.xa.maxTransactions";

    // HikariCP default connection pool settings - optimized for high concurrency
    // ISSUE #29 FIX: Updated these values to prevent indefinite blocking under high load
    public static final int DEFAULT_MAXIMUM_POOL_SIZE = 20;  // Increased from 10 to handle more concurrent requests
    public static final int DEFAULT_MINIMUM_IDLE = 5;        // Reduced from 10 to allow pool to scale down
    public static final long DEFAULT_IDLE_TIMEOUT = 600000;  // 10 minutes
    public static final long DEFAULT_MAX_LIFETIME = 1800000; // 30 minutes  
    public static final long DEFAULT_CONNECTION_TIMEOUT = 10000; // Reduced from 30s to 10s for faster failure
    
    // XA Transaction settings
    public static final int DEFAULT_MAX_XA_TRANSACTIONS = 50;  // Maximum concurrent XA transactions
    public static final long DEFAULT_XA_START_TIMEOUT_MILLIS = 60000;  // 60 seconds timeout for acquiring XA slot
}
