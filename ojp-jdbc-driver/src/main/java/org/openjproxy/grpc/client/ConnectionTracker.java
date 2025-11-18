package org.openjproxy.grpc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks connections and their bound servers in multinode deployments.
 * Uses a simple ConcurrentHashMap for thread-safe tracking.
 * Iteration only happens when needed (e.g., during redistribution).
 */
public class ConnectionTracker {
    
    private static final Logger log = LoggerFactory.getLogger(ConnectionTracker.class);
    
    private final Map<Connection, ServerEndpoint> connectionToServerMap;
    
    public ConnectionTracker() {
        this.connectionToServerMap = new ConcurrentHashMap<>();
    }
    
    /**
     * Registers a connection with its bound server.
     * 
     * @param connection The connection to register
     * @param server The server endpoint the connection is bound to
     */
    public void register(Connection connection, ServerEndpoint server) {
        if (connection == null || server == null) {
            log.warn("Attempted to register null connection or server");
            return;
        }
        
        connectionToServerMap.put(connection, server);
        log.debug("Registered connection to {}, total tracked: {}", 
                server.getAddress(), connectionToServerMap.size());
    }
    
    /**
     * Unregisters a connection when it's closed.
     * 
     * @param connection The connection to unregister
     */
    public void unregister(Connection connection) {
        if (connection == null) {
            return;
        }
        
        ServerEndpoint removed = connectionToServerMap.remove(connection);
        if (removed != null) {
            log.debug("Unregistered connection to {}, total tracked: {}", 
                    removed.getAddress(), connectionToServerMap.size());
        }
    }
    
    /**
     * Gets the current distribution of connections across servers.
     * This method iterates over all connections - only call when needed (e.g., during redistribution).
     * 
     * @return Map of server endpoints to their list of connections
     */
    public Map<ServerEndpoint, List<Connection>> getDistribution() {
        return connectionToServerMap.entrySet().stream()
                .collect(Collectors.groupingBy(
                    Map.Entry::getValue,
                    Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
    }
    
    /**
     * Gets the connection count per server.
     * Useful for logging and monitoring without needing full connection lists.
     * 
     * @return Map of server endpoints to connection counts
     */
    public Map<ServerEndpoint, Integer> getCounts() {
        Map<ServerEndpoint, Integer> counts = new HashMap<>();
        connectionToServerMap.values().forEach(server -> 
            counts.merge(server, 1, Integer::sum));
        return counts;
    }
    
    /**
     * Gets the total number of tracked connections.
     * 
     * @return Total connection count
     */
    public int getTotalConnections() {
        return connectionToServerMap.size();
    }
    
    /**
     * Gets the server endpoint a connection is bound to.
     * 
     * @param connection The connection to query
     * @return The server endpoint, or null if not tracked
     */
    public ServerEndpoint getBoundServer(Connection connection) {
        return connectionToServerMap.get(connection);
    }
    
    /**
     * Checks if a connection is currently tracked.
     * 
     * @param connection The connection to check
     * @return true if tracked, false otherwise
     */
    public boolean isTracked(Connection connection) {
        return connectionToServerMap.containsKey(connection);
    }
    
    /**
     * Clears all tracked connections.
     * Should only be used during shutdown or testing.
     */
    public void clear() {
        int count = connectionToServerMap.size();
        connectionToServerMap.clear();
        log.info("Cleared {} tracked connections", count);
    }
    
    /**
     * Inner class to hold connection information for redistribution.
     */
    public static class ConnectionInfo {
        private final String connectionUUID;
        private final String boundServerAddress;
        private final long lastUsedTime;
        
        public ConnectionInfo(String connectionUUID, String boundServerAddress, long lastUsedTime) {
            this.connectionUUID = connectionUUID;
            this.boundServerAddress = boundServerAddress;
            this.lastUsedTime = lastUsedTime;
        }
        
        public String getConnectionUUID() {
            return connectionUUID;
        }
        
        public String getBoundServerAddress() {
            return boundServerAddress;
        }
        
        public long getLastUsedTime() {
            return lastUsedTime;
        }
    }
    
    /**
     * Gets all XA connections with their metadata for redistribution.
     * Returns a list of ConnectionInfo objects containing UUID, server address, and last used time.
     * 
     * @return List of ConnectionInfo for all tracked XA connections
     */
    public List<ConnectionInfo> getAllXAConnections() {
        // For now, return all connections as we don't distinguish between XA and non-XA
        // In a full implementation, we would filter by connection type
        return connectionToServerMap.entrySet().stream()
                .map(entry -> {
                    Connection conn = entry.getKey();
                    ServerEndpoint server = entry.getValue();
                    // Use System.identityHashCode as a simple UUID proxy
                    String uuid = String.valueOf(System.identityHashCode(conn));
                    String serverAddr = server.getHost() + ":" + server.getPort();
                    // For now, use current time - in a full implementation, track actual last used time
                    long lastUsed = System.currentTimeMillis();
                    return new ConnectionInfo(uuid, serverAddr, lastUsed);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Closes an idle XA connection by its UUID.
     * This is called by the redistributor to selectively close connections for rebalancing.
     * 
     * @param connectionUUID The UUID of the connection to close
     * @throws SQLException if the connection cannot be closed
     */
    public void closeIdleConnection(String connectionUUID) throws SQLException {
        // Find the connection by UUID (using identity hash code)
        Connection toClose = null;
        for (Connection conn : connectionToServerMap.keySet()) {
            if (String.valueOf(System.identityHashCode(conn)).equals(connectionUUID)) {
                toClose = conn;
                break;
            }
        }
        
        if (toClose != null) {
            try {
                // Remove from tracking first
                connectionToServerMap.remove(toClose);
                // Close the connection
                toClose.close();
                log.debug("Closed idle connection {}", connectionUUID);
            } catch (SQLException e) {
                log.error("Failed to close connection {}: {}", connectionUUID, e.getMessage());
                throw e;
            }
        } else {
            log.warn("Connection {} not found for closing", connectionUUID);
        }
    }
}
