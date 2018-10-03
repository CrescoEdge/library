package io.cresco.library.db;

import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;
import java.net.URL;
import java.util.List;
import java.util.Properties;
//This is an example found at https://vladmihalcea.com/how-to-bootstrap-jpa-programmatically-without-the-persistence-xml-configuration-file/
public class PersistenceUnitInfoImpl implements PersistenceUnitInfo {
    public static final String JPA_VERSION = "2.2";
    private String persistenceUnitName
            ,persistenceProviderClassName;
    private PersistenceUnitTransactionType persistenceUnitTransactionType;
    DataSource jtaDataSource
            ,nonJtaDataSource;
    private List<String> mappingFileNames
            ,managedClassNames;
    private List<URL> jarFileUrls;
    private SharedCacheMode sharedCacheMode;
    private ValidationMode validationMode;
    private Properties properties;
    private boolean excludeUnlistedClasses;
    private URL persistenceUnitRootUrl;

    protected PersistenceUnitInfoImpl(String persistenceUnitName
            ,String persistenceProviderClassName
            ,PersistenceUnitTransactionType persistenceUnitTransactionType
            ,DataSource jtaDataSource
            ,DataSource nonJtaDataSource
            ,List<String> mappingFileNames
            ,List<String> managedClassNames
            ,List<URL> jarFileUrls
            ,SharedCacheMode sharedCacheMode
            ,ValidationMode validationMode
            ,Properties properties
            ,boolean excludeUnlistedClasses
            ,URL persistenceUnitRootUrl) {
        this.persistenceUnitName = persistenceUnitName;
        this.persistenceProviderClassName = persistenceProviderClassName;
        this.persistenceUnitTransactionType = persistenceUnitTransactionType;
        this.jtaDataSource = jtaDataSource;
        this.nonJtaDataSource = nonJtaDataSource;
        this.mappingFileNames = mappingFileNames;
        this.managedClassNames = managedClassNames;
        this.jarFileUrls = jarFileUrls;
        this.sharedCacheMode = sharedCacheMode;
        this.validationMode = validationMode;
        this.properties = properties;
        this.excludeUnlistedClasses = excludeUnlistedClasses;
        this.persistenceUnitRootUrl = persistenceUnitRootUrl;
    }

    @Override
    public String getPersistenceUnitName() {
        return persistenceUnitName;
    }

    @Override
    public String getPersistenceProviderClassName() {
        return persistenceProviderClassName;
    }

    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        return persistenceUnitTransactionType;
    }

    @Override
    public DataSource getJtaDataSource() {
        return jtaDataSource;
    }

    @Override
    public DataSource getNonJtaDataSource() {
        return nonJtaDataSource;
    }

    @Override
    public List<String> getMappingFileNames() {
        return mappingFileNames;
    }

    @Override
    public List<URL> getJarFileUrls() {
        return jarFileUrls;
    }

    @Override
    public URL getPersistenceUnitRootUrl() {
        return persistenceUnitRootUrl;
    }

    @Override
    public List<String> getManagedClassNames() {
        return managedClassNames;
    }

    @Override
    public boolean excludeUnlistedClasses() {
        return excludeUnlistedClasses;
    }

    @Override
    public SharedCacheMode getSharedCacheMode() {
        return sharedCacheMode;
    }

    @Override
    public ValidationMode getValidationMode() {
        return validationMode;
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    @Override
    public String getPersistenceXMLSchemaVersion() {
        return JPA_VERSION;
    }

    @Override
    public ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    @Override
    public void addTransformer(ClassTransformer classTransformer) {
        //Do nothing. Not sure if this method is relevant outside of an Java EE container
    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        return null;
    }
}
