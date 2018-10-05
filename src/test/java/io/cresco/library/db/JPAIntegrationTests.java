package io.cresco.library.db;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.platform.commons.util.ExceptionUtils;


import javax.persistence.spi.PersistenceUnitInfo;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;
import java.sql.SQLException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JPAIntegrationTests {
    PooledConnection testDbConn;

    DataSource getDataSource(){
        String testResourcesPath = "../src/test/resources";
        String testdbName = "testdb";
        String testdbURL = "jdbc:h2:"+testResourcesPath+"/"+testdbName;
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(testdbURL);
        ds.setUser("testuser");
        ds.setPassword("test");
        return ds;
    }

    @BeforeAll
    void setup(){
        PersistenceUnitInfo puInfo = new PersistenceUnitInfoBuilder("testPU",
                "org.hibernate.db.jpa.HibernatePersistenceProvider")
                .setNonJtaDataSource(getDataSource())
                .build();

    }

    @Test
    void testInit(){
        //TODO: Add methods from CoreDBImpl experimental project for bootstrapping JPA
        System.out.println("Did some stuff");

    }

    @AfterAll
    void teardown(){
        try {
            if(testDbConn != null) testDbConn.close();
        } catch (SQLException ex){
            System.out.println(ExceptionUtils.readStackTrace(ex));
        }
    }
}
