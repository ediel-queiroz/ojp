package org.openjproxy.grpc.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.netty.NettyServerBuilder;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import org.openjproxy.constants.CommonConstants;
import org.openjproxy.grpc.server.pool.AtomikosLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GrpcServer {
    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        // Initialize health status manager
        OjpHealthManager.initialize();

        // Load configuration
        ServerConfiguration config = new ServerConfiguration();
        
        // Initialize Atomikos transaction manager for XA support
        try {
            AtomikosLifecycle.start(config.isAtomikosLoggingEnabled(), config.getAtomikosLoggingDir());
            logger.info("Atomikos transaction manager initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize Atomikos transaction manager: {}", e.getMessage(), e);
            // Continue without XA support - only XA connections will fail
            logger.warn("Server will continue without XA transaction support");
        }
        
        // Validate IP whitelist for server
        if (!IpWhitelistValidator.validateWhitelistRules(config.getAllowedIps())) {
            logger.error("Invalid IP whitelist configuration for server. Exiting.");
            System.exit(1);
        }

        // Initialize telemetry based on configuration
        OjpServerTelemetry ojpServerTelemetry = new OjpServerTelemetry();
        GrpcTelemetry grpcTelemetry;
        
        if (config.isOpenTelemetryEnabled()) {
            grpcTelemetry = ojpServerTelemetry.createGrpcTelemetry(
                config.getPrometheusPort(), 
                config.getPrometheusAllowedIps()
            );

            OjpHealthManager.setServiceStatus(OjpHealthManager.Services.OPENTELEMETRY_SERVICE,
                    HealthCheckResponse.ServingStatus.SERVING);
        } else {
            grpcTelemetry = ojpServerTelemetry.createNoOpGrpcTelemetry();
        }

        // Build server with configuration
        SessionManagerImpl sessionManager = new SessionManagerImpl();
        
        ServerBuilder<?> serverBuilder = NettyServerBuilder
                .forPort(config.getServerPort())
                .executor(Executors.newFixedThreadPool(config.getThreadPoolSize()))
                .maxInboundMessageSize(config.getMaxRequestSize())
                .keepAliveTime(config.getConnectionIdleTimeout(), TimeUnit.MILLISECONDS)
                .addService(new StatementServiceImpl(
                        sessionManager,
                        new CircuitBreaker(config.getCircuitBreakerTimeout(), config.getCircuitBreakerThreshold()),
                        config
                ))
                .addService(OjpHealthManager.getHealthStatusManager().getHealthService())
                .intercept(grpcTelemetry.newServerInterceptor());

        Server server = serverBuilder.build();

        logger.info("Starting OJP gRPC Server on port {}", config.getServerPort());
        logger.info("Server configuration applied successfully");
        
        server.start();
        OjpHealthManager.setServiceStatus(OjpHealthManager.Services.OJP_SERVER,
                HealthCheckResponse.ServingStatus.SERVING);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down OJP gRPC Server...");
            server.shutdown();

            try {
                if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("Server did not terminate gracefully, forcing shutdown");
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for server shutdown");
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            // Shutdown Atomikos transaction manager
            try {
                AtomikosLifecycle.stop();
                logger.info("Atomikos transaction manager shutdown complete");
            } catch (Exception e) {
                logger.warn("Error shutting down Atomikos transaction manager: {}", e.getMessage());
            }
            
            logger.info("OJP gRPC Server shutdown complete");
        }));

        logger.info("OJP gRPC Server started successfully and awaiting termination");
        server.awaitTermination();
    }
}
