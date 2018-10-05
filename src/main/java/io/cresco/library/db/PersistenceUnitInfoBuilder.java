package io.cresco.library.db;

import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PersistenceUnitInfoBuilder {
    private String persistenceUnitName
            ,persistenceProviderClassName;
    private PersistenceUnitTransactionType persistenceUnitTransactionType =
            PersistenceUnitTransactionType.RESOURCE_LOCAL;
    private DataSource jtaDataSource, nonJtaDataSource;
    private List<String> mappingFileNames = new ArrayList<>();
    private List<String> managedClassNames = new ArrayList<>();
    private List<URL> jarFileUrls = new ArrayList<>();
    private SharedCacheMode sharedCacheMode = SharedCacheMode.UNSPECIFIED;
    private ValidationMode validationMode = ValidationMode.AUTO;
    private Properties properties = new Properties();
    private boolean excludeUnlistedClasses = false;
    private URL persistenceUnitRootUrl = ClassLoader.getSystemClassLoader().getResource(".");

    public PersistenceUnitInfoBuilder(String persistenceUnitName,
                                      String persistenceProviderClassName){
        this.persistenceUnitName = persistenceUnitName;
        this.persistenceProviderClassName = persistenceProviderClassName;
    }

    public PersistenceUnitInfoBuilder setPersistenceUnitTransactionType(
            PersistenceUnitTransactionType persistenceUnitTransactionType) {
        this.persistenceUnitTransactionType = persistenceUnitTransactionType;
        return this;
    }

    public PersistenceUnitInfoBuilder setJtaDataSource(DataSource jtaDataSource) {
        this.jtaDataSource = jtaDataSource;
        this.nonJtaDataSource = null;
        this.persistenceUnitTransactionType = PersistenceUnitTransactionType.JTA;
        return this;
    }

    public PersistenceUnitInfoBuilder setNonJtaDataSource(DataSource nonJtaDataSource) {
        this.nonJtaDataSource = nonJtaDataSource;
        this.jtaDataSource = null;
        this.persistenceUnitTransactionType = PersistenceUnitTransactionType.RESOURCE_LOCAL;
        return this;
    }

    public PersistenceUnitInfoBuilder setMappingFileNames(List<String> mappingFileNames) {
        this.mappingFileNames = mappingFileNames;
        return this;
    }

    public PersistenceUnitInfoBuilder setManagedClassNames(List<String> managedClassNames) {
        this.managedClassNames = managedClassNames;
        return this;
    }

    public PersistenceUnitInfoBuilder setJarFileUrls(List<URL> jarFileUrls) {
        this.jarFileUrls = jarFileUrls;
        return this;
    }

    public PersistenceUnitInfoBuilder setSharedCacheMode(SharedCacheMode sharedCacheMode) {
        this.sharedCacheMode = sharedCacheMode;
        return this;
    }

    public PersistenceUnitInfoBuilder setValidationMode(ValidationMode validationMode) {
        this.validationMode = validationMode;
        return this;
    }

    public PersistenceUnitInfoBuilder setProperties(Properties properties) {
        this.properties = properties;
        return this;
    }

    public PersistenceUnitInfoBuilder setExcludeUnlistedClasses(boolean excludeUnlistedClasses){
        this.excludeUnlistedClasses = excludeUnlistedClasses;
        return this;
    }

    public PersistenceUnitInfoBuilder setPersistenceUnitRootUrl(URL theURL){
        this.persistenceUnitRootUrl = theURL;
        return this;
    }

    public PersistenceUnitInfoImpl build() {
        if(this.jtaDataSource != null && this.nonJtaDataSource != null){
            throw new IllegalArgumentException("Cannot specify jtaDataSource and nonJtaDataSource"+
                    "for persistence unit %s".format(this.persistenceUnitName));
        }
        if(this.persistenceUnitTransactionType == PersistenceUnitTransactionType.RESOURCE_LOCAL && this.jtaDataSource != null){
            throw new IllegalArgumentException("Cannot specify transaction type 'RESOURCE_LOCAL' with a JTA DataSource");
        }
        if(this.persistenceUnitTransactionType == PersistenceUnitTransactionType.JTA && this.nonJtaDataSource != null){
            throw new IllegalArgumentException("Cannot specify transaction type 'JTA' with non-JTA DataSource");
        }
        return new PersistenceUnitInfoImpl(persistenceUnitName
                ,persistenceProviderClassName
                ,persistenceUnitTransactionType
                ,jtaDataSource
                ,nonJtaDataSource
                ,mappingFileNames
                ,managedClassNames
                ,jarFileUrls
                ,sharedCacheMode
                ,validationMode
                ,properties
                ,excludeUnlistedClasses
                ,persistenceUnitRootUrl
                 );
    }
}