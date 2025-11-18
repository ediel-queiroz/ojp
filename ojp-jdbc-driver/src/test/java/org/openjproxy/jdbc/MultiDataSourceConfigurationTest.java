package org.openjproxy.jdbc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for multi-datasource configuration functionality.
 */
public class MultiDataSourceConfigurationTest {

    @Test
    public void testUrlParsingWithDataSourceParameter() throws Exception {
        // Test URL without dataSource parameter - should default to "default"
        UrlParser.UrlParseResult result1 = UrlParser.parseUrlWithDataSource("jdbc:ojp[localhost:1059]_h2:~/test");
        
        assertEquals("jdbc:ojp[localhost:1059]_h2:~/test", result1.cleanUrl);
        assertEquals("default", result1.dataSourceName);
        
        // Test URL with dataSource parameter in parentheses
        UrlParser.UrlParseResult result2 = UrlParser.parseUrlWithDataSource("jdbc:ojp[localhost:1059(myApp)]_h2:~/test");
        
        assertEquals("jdbc:ojp[localhost:1059]_h2:~/test", result2.cleanUrl);
        assertEquals("myApp", result2.dataSourceName);
        
        // Test URL with port and datasource
        UrlParser.UrlParseResult result3 = UrlParser.parseUrlWithDataSource("jdbc:ojp[localhost:1059(readOnly)]_h2:~/test");
        
        assertEquals("jdbc:ojp[localhost:1059]_h2:~/test", result3.cleanUrl);
        assertEquals("readOnly", result3.dataSourceName);
        
        // Test URL with spaces around datasource name - should be trimmed
        UrlParser.UrlParseResult result4 = UrlParser.parseUrlWithDataSource("jdbc:ojp[localhost:1059( myApp )]_h2:~/test");
        
        assertEquals("jdbc:ojp[localhost:1059]_h2:~/test", result4.cleanUrl);
        assertEquals("myApp", result4.dataSourceName); // Should be trimmed
    }

    @Test
    public void testMultinodeUrlParsingWithDataSourceParameter() throws Exception {
        // Test URL without dataSource parameter - should default to "default"
        UrlParser.UrlParseResult result1 = UrlParser.parseUrlWithDataSource("jdbc:ojp[localhost:10591(default),localhost:10592(multinode)]_h2:~/test");

        assertEquals("jdbc:ojp[localhost:10591,localhost:10592]_h2:~/test", result1.cleanUrl);
        assertEquals("default", result1.dataSourceNames.get(0));
        assertEquals("multinode", result1.dataSourceNames.get(1));
    }

    
    @Test
    public void testLoadOjpPropertiesForDataSource() throws Exception {
        // Create test properties with multiple datasources
        String testProperties = 
            "# Default datasource\n" +
            "ojp.connection.pool.maximumPoolSize=20\n" +
            "ojp.connection.pool.minimumIdle=5\n" +
            "\n" +
            "# MyApp datasource\n" +
            "myApp.ojp.connection.pool.maximumPoolSize=50\n" +
            "myApp.ojp.connection.pool.minimumIdle=10\n" +
            "myApp.ojp.connection.pool.connectionTimeout=15000\n" +
            "\n" +
            "# ReadOnly datasource\n" +
            "readOnly.ojp.connection.pool.maximumPoolSize=5\n" +
            "readOnly.ojp.connection.pool.minimumIdle=1\n";
        
        // Mock the loadOjpProperties method to return our test properties
        Properties allProperties = new Properties();
        try (InputStream is = new ByteArrayInputStream(testProperties.getBytes())) {
            allProperties.load(is);
        }
        
        // Create a Driver instance that can load properties
        TestDriver driver = new TestDriver(allProperties);
        
        // Test loading properties for "myApp" datasource
        Properties myAppProps = driver.testLoadOjpPropertiesForDataSource("myApp");
        assertNotNull(myAppProps);
        assertEquals("50", myAppProps.getProperty("ojp.connection.pool.maximumPoolSize"));
        assertEquals("10", myAppProps.getProperty("ojp.connection.pool.minimumIdle"));
        assertEquals("15000", myAppProps.getProperty("ojp.connection.pool.connectionTimeout"));
        assertEquals("myApp", myAppProps.getProperty("ojp.datasource.name"));
        
        // Test loading properties for "readOnly" datasource
        Properties readOnlyProps = driver.testLoadOjpPropertiesForDataSource("readOnly");
        assertNotNull(readOnlyProps);
        assertEquals("5", readOnlyProps.getProperty("ojp.connection.pool.maximumPoolSize"));
        assertEquals("1", readOnlyProps.getProperty("ojp.connection.pool.minimumIdle"));
        assertEquals("readOnly", readOnlyProps.getProperty("ojp.datasource.name"));
        
        // Test loading properties for "default" datasource
        Properties defaultProps = driver.testLoadOjpPropertiesForDataSource("default");
        assertNotNull(defaultProps);
        assertEquals("20", defaultProps.getProperty("ojp.connection.pool.maximumPoolSize"));
        assertEquals("5", defaultProps.getProperty("ojp.connection.pool.minimumIdle"));
        assertEquals("default", defaultProps.getProperty("ojp.datasource.name"));
        
        // Test loading properties for non-existent datasource
        Properties nonExistentProps = driver.testLoadOjpPropertiesForDataSource("nonExistent");
        assertNull(nonExistentProps);
    }
    
    @Test
    public void testDataSourceConfigurationWithNoProperties() throws Exception {
        // Create a driver that returns null for ojp.properties (simulating no file found)
        TestDriver driver = new TestDriver(null);
        
        // Test when no properties file exists
        Properties result = driver.testLoadOjpPropertiesForDataSource("default");
        assertNull(result);
        
        // Test with non-default datasource when no properties exist
        Properties result2 = driver.testLoadOjpPropertiesForDataSource("myApp");
        assertNull(result2);
    }
    
    /**
     * Test driver class that exposes protected methods for testing
     */
    private static class TestDriver extends Driver {
        private final Properties mockProperties;
        
        public TestDriver(Properties mockProperties) {
            super();
            this.mockProperties = mockProperties;
        }
        
        @Override
        protected Properties loadOjpProperties() {
            return mockProperties;
        }
        
        public Properties testLoadOjpPropertiesForDataSource(String dataSourceName) {
            return super.loadOjpPropertiesForDataSource(dataSourceName);
        }
    }
}
