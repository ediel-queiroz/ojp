package org.openjproxy.jdbc.xa;

import com.google.protobuf.ByteString;
import com.openjproxy.grpc.ConnectionDetails;
import com.openjproxy.grpc.SessionInfo;
import lombok.extern.slf4j.Slf4j;
import org.openjproxy.grpc.SerializationHandler;
import org.openjproxy.grpc.client.StatementService;
import org.openjproxy.jdbc.ClientUUID;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Implementation of XAConnection that connects to the OJP server for XA operations.
 * Uses the integrated StatementService for connection management.
 * 
 * <p>The server-side session is created lazily when first needed (either when getting
 * the XAResource or when getting a Connection), to avoid creating unnecessary sessions.
 */
@Slf4j
public class OjpXAConnection implements XAConnection {

    private final StatementService statementService;
    private SessionInfo sessionInfo; // Lazily initialized
    private final String url;
    private final String user;
    private final String password;
    private final Properties properties;
    private Connection logicalConnection;
    private OjpXAResource xaResource;
    private boolean closed = false;
    private final List<ConnectionEventListener> listeners = new ArrayList<>();

    public OjpXAConnection(StatementService statementService, String url, String user, String password, Properties properties) {
        log.debug("Creating OjpXAConnection for URL: {}", url);
        this.statementService = statementService;
        this.url = url;
        this.user = user;
        this.password = password;
        this.properties = properties;
        // Session is created lazily when needed
    }
    
    /**
     * Lazily create the server-side session when first needed.
     * This avoids creating sessions that may never be used.
     */
    private synchronized SessionInfo getOrCreateSession() throws SQLException {
        if (sessionInfo != null) {
            return sessionInfo;
        }
        
        try {
            // Connect to server with XA flag enabled
            ByteString propertiesBytes = ByteString.EMPTY;
            if (properties != null && !properties.isEmpty()) {
                propertiesBytes = ByteString.copyFrom(SerializationHandler.serialize(properties));
            }

            ConnectionDetails connectionDetails = ConnectionDetails.newBuilder()
                    .setUrl(url)
                    .setUser(user != null ? user : "")
                    .setPassword(password != null ? password : "")
                    .setClientUUID(ClientUUID.getUUID())
                    .setProperties(propertiesBytes)
                    .setIsXA(true)  // Mark this as an XA connection
                    .build();

            this.sessionInfo = statementService.connect(connectionDetails);
            log.debug("XA connection established with session: {}", sessionInfo.getSessionUUID());
            return sessionInfo;

        } catch (Exception e) {
            log.error("Failed to create XA connection session", e);
            throw new SQLException("Failed to create XA connection session", e);
        }
    }

    @Override
    public XAResource getXAResource() throws SQLException {
        log.debug("getXAResource called");
        checkClosed();
        if (xaResource == null) {
            // Lazily create session when XAResource is first requested
            SessionInfo session = getOrCreateSession();
            xaResource = new OjpXAResource(statementService, session);
        }
        return xaResource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        log.debug("getConnection called");
        checkClosed();
        
        // Close any existing logical connection
        if (logicalConnection != null && !logicalConnection.isClosed()) {
            logicalConnection.close();
        }
        
        // Lazily create session when Connection is first requested
        SessionInfo session = getOrCreateSession();
        
        // Create a new logical connection that uses the same XA session on the server
        logicalConnection = new OjpXALogicalConnection(this, session, url);
        return logicalConnection;
    }
    
    /**
     * Get the statement service for this XA connection.
     */
    StatementService getStatementService() {
        return statementService;
    }

    @Override
    public void close() throws SQLException {
        log.debug("close called");
        if (closed) {
            return;
        }
        
        closed = true;
        
        // Close logical connection if open
        if (logicalConnection != null && !logicalConnection.isClosed()) {
            logicalConnection.close();
        }
        
        // Notify listeners
        ConnectionEvent event = new ConnectionEvent(this);
        for (ConnectionEventListener listener : listeners) {
            listener.connectionClosed(event);
        }
        
        // Close XA session on server (only if it was created)
        if (sessionInfo != null) {
            try {
                statementService.terminateSession(sessionInfo);
            } catch (Exception e) {
                log.error("Error closing XA session", e);
                throw new SQLException("Error closing XA session", e);
            }
        }
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        log.debug("addConnectionEventListener called");
        listeners.add(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        log.debug("removeConnectionEventListener called");
        listeners.remove(listener);
    }

    @Override
    public void addStatementEventListener(javax.sql.StatementEventListener listener) {
        log.debug("addStatementEventListener called - not supported");
        // Not supported for XA connections
    }

    @Override
    public void removeStatementEventListener(javax.sql.StatementEventListener listener) {
        log.debug("removeStatementEventListener called - not supported");
        // Not supported for XA connections
    }

    /**
     * Notify listeners of a connection error.
     */
    void notifyError(SQLException exception) {
        ConnectionEvent event = new ConnectionEvent(this, exception);
        for (ConnectionEventListener listener : listeners) {
            listener.connectionErrorOccurred(event);
        }
    }

    /**
     * Get the session info for this XA connection.
     */
    SessionInfo getSessionInfo() {
        return sessionInfo;
    }

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("XA Connection is closed");
        }
    }
}
