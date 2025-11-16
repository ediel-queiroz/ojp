package org.openjproxy.jdbc;

import com.google.protobuf.ByteString;
import com.openjproxy.grpc.ConnectionDetails;
import com.openjproxy.grpc.SessionInfo;
import lombok.extern.slf4j.Slf4j;
import org.openjproxy.database.DatabaseUtils;
import org.openjproxy.grpc.ProtoConverter;
import org.openjproxy.grpc.client.MultinodeConnectionManager;
import org.openjproxy.grpc.client.MultinodeStatementService;
import org.openjproxy.grpc.client.MultinodeUrlParser;
import org.openjproxy.grpc.client.ServerEndpoint;
import org.openjproxy.grpc.client.StatementService;
import org.openjproxy.grpc.client.StatementServiceGrpcClient;

import java.io.IOException;
import java.io.InputStream;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static org.openjproxy.jdbc.Constants.PASSWORD;
import static org.openjproxy.jdbc.Constants.USER;

@Slf4j
public class Driver implements java.sql.Driver {

    static {
        try {
            log.debug("Registering OpenJProxy Driver");
            DriverManager.registerDriver(new Driver());
        } catch (SQLException var1) {
            log.error("Can't register OJP driver!", var1);
        }
    }

    public Driver() {
        // Services are created per-URL configuration in connect()
    }

    @Override
    public java.sql.Connection connect(String url, Properties info) throws SQLException {
        log.debug("connect: url={}, info={}", url, info);
        
        // Parse URL to extract dataSource name(s) and clean URL
        UrlParser.UrlParseResult urlParseResult = UrlParser.parseUrlWithDataSource(url);
        String cleanUrl = urlParseResult.cleanUrl;
        String dataSourceName = urlParseResult.dataSourceName;
        List<String> dataSourceNames = urlParseResult.dataSourceNames;
        
        log.debug("Parsed URL - clean: {}, dataSource: {}, dataSources: {}", cleanUrl, dataSourceName, dataSourceNames);
        
        // Detect multinode vs single-node configuration and get the URL to use for connection
        MultinodeUrlParser.ServiceAndUrl serviceAndUrl = MultinodeUrlParser.getOrCreateStatementService(cleanUrl, dataSourceNames);
        StatementService statementService = serviceAndUrl.getService();
        String connectionUrl = serviceAndUrl.getConnectionUrl();
        List<String> serverEndpoints = serviceAndUrl.getServerEndpoints();
        List<ServerEndpoint> serverEndpointsWithDatasources = serviceAndUrl.getServerEndpointsWithDatasources();
        
        // For multinode with per-endpoint datasources, we need to handle connection differently
        // For now, we'll use the first datasource for the initial connection setup
        // TODO: Enhance to support per-endpoint configuration passing to servers
        if (serverEndpointsWithDatasources != null && serverEndpointsWithDatasources.size() > 1) {
            boolean hasMultipleDatasources = serverEndpointsWithDatasources.stream()
                .map(ServerEndpoint::getDataSourceName)
                .distinct()
                .count() > 1;
            
            if (hasMultipleDatasources) {
                log.warn("Per-endpoint datasources detected. Currently using first datasource '{}' for connection properties. " +
                        "Per-server configuration will be applied based on server endpoint datasource names: {}", 
                        dataSourceName,
                        serverEndpointsWithDatasources.stream()
                            .map(ep -> ep.getAddress() + "=" + ep.getDataSourceName())
                            .collect(java.util.stream.Collectors.joining(", ")));
            }
        }
        
        // Load ojp.properties file and extract datasource-specific configuration
        Properties ojpProperties = loadOjpPropertiesForDataSource(dataSourceName);
        
        ConnectionDetails.Builder connBuilder = ConnectionDetails.newBuilder()
                .setUrl(connectionUrl)  // Use the possibly-modified URL with single endpoint
                .setUser((String) ((info.get(USER) != null)? info.get(USER) : ""))
                .setPassword((String) ((info.get(PASSWORD) != null) ? info.get(PASSWORD) : ""))
                .setClientUUID(ClientUUID.getUUID());
        
        // Add server endpoints list for multinode coordination
        if (serverEndpoints != null && !serverEndpoints.isEmpty()) {
            connBuilder.addAllServerEndpoints(serverEndpoints);
            log.info("Adding {} server endpoints to ConnectionDetails for multinode coordination", serverEndpoints.size());
        }
        
        if (ojpProperties != null && !ojpProperties.isEmpty()) {
            // Convert Properties to Map<String, Object>
            Map<String, Object> propertiesMap = new HashMap<>();
            for (String key : ojpProperties.stringPropertyNames()) {
                propertiesMap.put(key, ojpProperties.getProperty(key));
            }
            connBuilder.addAllProperties(ProtoConverter.propertiesToProto(propertiesMap));
            log.debug("Loaded ojp.properties with {} properties for dataSource: {}", ojpProperties.size(), dataSourceName);
        }
        
        log.info("Calling connect() on statement service with URL: {}", connectionUrl);
        SessionInfo sessionInfo;
        try {
            sessionInfo = statementService.connect(connBuilder.build());
            log.info("Connection established - sessionUUID: {}, connHash: {}", 
                    sessionInfo.getSessionUUID(), sessionInfo.getConnHash());
        } catch (Exception e) {
            log.error("Failed to establish connection", e);
            throw e;
        }
        log.debug("Returning new Connection with sessionInfo: {}", sessionInfo);
        return new Connection(sessionInfo, statementService, DatabaseUtils.resolveDbName(cleanUrl));
    }
    
    
    /**
     * Load ojp.properties and extract configuration specific to the given dataSource.
     */
    protected Properties loadOjpPropertiesForDataSource(String dataSourceName) {
        Properties allProperties = loadOjpProperties();
        if (allProperties == null || allProperties.isEmpty()) {
            return null;
        }
        
        Properties dataSourceProperties = new Properties();
        
        // Look for dataSource-prefixed properties first: {dataSourceName}.ojp.connection.pool.*
        String prefix = dataSourceName + ".ojp.connection.pool.";
        boolean foundDataSourceSpecific = false;
        
        for (String key : allProperties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                // Remove the dataSource prefix and keep the standard property name
                String standardKey = key.substring(dataSourceName.length() + 1); // Remove "{dataSourceName}."
                dataSourceProperties.setProperty(standardKey, allProperties.getProperty(key));
                foundDataSourceSpecific = true;
            }
        }
        
        // If no dataSource-specific properties found, and this is the "default" dataSource,
        // look for unprefixed properties: ojp.connection.pool.*
        if (!foundDataSourceSpecific && "default".equals(dataSourceName)) {
            for (String key : allProperties.stringPropertyNames()) {
                if (key.startsWith("ojp.connection.pool.")) {
                    dataSourceProperties.setProperty(key, allProperties.getProperty(key));
                }
            }
        }
        
        // If we found any properties, also include the dataSource name as a single property
        // Note: The dataSource-prefixed properties (e.g., "webApp.ojp.connection.pool.*") 
        // are sent to the server with their prefixes removed (e.g., "ojp.connection.pool.*"),
        // and the dataSource name itself is sent separately as "ojp.datasource.name"
        if (!dataSourceProperties.isEmpty()) {
            dataSourceProperties.setProperty("ojp.datasource.name", dataSourceName);
        }
        
        log.debug("Loaded {} properties for dataSource '{}': {}", 
                dataSourceProperties.size(), dataSourceName, dataSourceProperties);
        
        return dataSourceProperties.isEmpty() ? null : dataSourceProperties;
    }

    /**
     * Load the raw ojp.properties file from classpath.
     */
    protected Properties loadOjpProperties() {
        Properties properties = new Properties();
        
        // Only try to load from resources/ojp.properties in the classpath
        try (InputStream is = Driver.class.getClassLoader().getResourceAsStream("ojp.properties")) {
            if (is != null) {
                properties.load(is);
                log.debug("Loaded ojp.properties from resources folder");
                return properties;
            }
        } catch (IOException e) {
            log.debug("Could not load ojp.properties from resources folder: {}", e.getMessage());
        }
        
        log.debug("No ojp.properties file found, using server defaults");
        return null;
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        log.debug("acceptsURL: {}", url);
        if (url == null) {
            log.error("URL is null");
            throw new SQLException("URL is null");
        } else {
            boolean accepts = url.startsWith("jdbc:ojp");
            log.debug("acceptsURL returns: {}", accepts);
            return accepts;
        }
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        log.debug("getPropertyInfo: url={}, info={}", url, info);
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        log.debug("getMajorVersion called");
        return 0;
    }

    @Override
    public int getMinorVersion() {
        log.debug("getMinorVersion called");
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        log.debug("jdbcCompliant called");
        return false;
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        log.debug("getParentLogger called");
        return null;
    }
}