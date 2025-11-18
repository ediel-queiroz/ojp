package org.openjproxy.grpc.client;

import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Redistributes XA connections across recovered servers to rebalance load.
 * Uses ConnectionTracker to identify idle connections and selectively closes them
 * based on configured thresholds.
 */
@Slf4j
public class XAConnectionRedistributor {
    
    private final MultinodeConnectionManager connectionManager;
    private final HealthCheckConfig healthConfig;
    
    public XAConnectionRedistributor(MultinodeConnectionManager connectionManager, 
                                    HealthCheckConfig healthConfig) {
        this.connectionManager = connectionManager;
        this.healthConfig = healthConfig;
    }
    
    /**
     * Rebalances connections when servers recover.
     * Closes a subset of idle connections on overloaded servers to allow natural
     * redistribution through connection pool recreation.
     * 
     * @param recoveredServers List of servers that have just recovered
     * @param allHealthyServers List of all currently healthy servers (including recovered ones)
     */
    public void rebalance(List<ServerEndpoint> recoveredServers, List<ServerEndpoint> allHealthyServers) {
        if (recoveredServers == null || recoveredServers.isEmpty()) {
            log.debug("No recovered servers to rebalance");
            return;
        }
        
        if (allHealthyServers == null || allHealthyServers.size() < 2) {
            log.debug("Not enough healthy servers for redistribution");
            return;
        }
        
        log.info("Starting XA connection redistribution for {} recovered server(s) among {} healthy servers", 
                recoveredServers.size(), allHealthyServers.size());
        
        try {
            ConnectionTracker tracker = connectionManager.getConnectionTracker();
            
            // Get current connection distribution
            List<ConnectionTracker.ConnectionInfo> allConnections = tracker.getAllXAConnections();
            
            if (allConnections.isEmpty()) {
                log.info("No XA connections to redistribute");
                return;
            }
            
            // Calculate target connections per server
            int totalConnections = allConnections.size();
            int targetPerServer = totalConnections / allHealthyServers.size();
            
            log.info("Total XA connections: {}, Target per server: {}", totalConnections, targetPerServer);
            
            // Group connections by server
            Map<String, List<ConnectionTracker.ConnectionInfo>> connectionsByServer = allConnections.stream()
                    .collect(Collectors.groupingBy(ConnectionTracker.ConnectionInfo::getBoundServerAddress));
            
            // Identify overloaded servers (servers with more than their fair share)
            double idleRebalanceFraction = healthConfig.getIdleRebalanceFraction();
            int maxClosePerRecovery = healthConfig.getMaxClosePerRecovery();
            int totalClosed = 0;
            
            // Sort servers by connection count (descending) to close from most loaded first
            List<String> overloadedServers = connectionsByServer.entrySet().stream()
                    .filter(entry -> entry.getValue().size() > targetPerServer)
                    .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            log.info("Found {} overloaded server(s)", overloadedServers.size());
            
            for (String serverAddress : overloadedServers) {
                if (totalClosed >= maxClosePerRecovery) {
                    log.info("Reached max close limit ({}), stopping redistribution", maxClosePerRecovery);
                    break;
                }
                
                List<ConnectionTracker.ConnectionInfo> serverConnections = connectionsByServer.get(serverAddress);
                int excessConnections = serverConnections.size() - targetPerServer;
                int toClose = (int) Math.ceil(excessConnections * idleRebalanceFraction);
                toClose = Math.min(toClose, maxClosePerRecovery - totalClosed);
                
                if (toClose <= 0) {
                    continue;
                }
                
                log.info("Server {} has {} connections ({} excess), closing {} idle connections", 
                        serverAddress, serverConnections.size(), excessConnections, toClose);
                
                // Sort by last used time (oldest first) and close idle connections
                List<ConnectionTracker.ConnectionInfo> idleConnections = serverConnections.stream()
                        .sorted(Comparator.comparingLong(ConnectionTracker.ConnectionInfo::getLastUsedTime))
                        .limit(toClose)
                        .collect(Collectors.toList());
                
                for (ConnectionTracker.ConnectionInfo connInfo : idleConnections) {
                    try {
                        tracker.closeIdleConnection(connInfo.getConnectionUUID());
                        totalClosed++;
                        log.debug("Closed idle connection {} from server {}", 
                                connInfo.getConnectionUUID(), serverAddress);
                    } catch (SQLException e) {
                        log.warn("Failed to close connection {}: {}", 
                                connInfo.getConnectionUUID(), e.getMessage());
                    }
                }
            }
            
            log.info("XA connection redistribution complete: closed {} connections", totalClosed);
            
        } catch (Exception e) {
            log.error("Error during XA connection redistribution: {}", e.getMessage(), e);
        }
    }
}
