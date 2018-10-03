package io.cresco.library.db;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


import javax.persistence.EntityManagerFactory;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tags({@Tag("jpastuff"),@Tag("unit")})
public class PersistenceUnitInfoImplTest {

    /*Writing lot of boilerplate code by hand irritates the piss out of me so I
    spent that time writing a snippet of Python to handle the necessary but very
    boring stuff.
    methods = ("PersistenceUnitName","PersistenceProviderClassName"
               ,"PersistenceUnitTransactionType","JtaDataSource"
               ,"NonJtaDataSource","MappingFileNames","ManagedClassNames"
               ,"SharedCacheMode","ValidationMode","Properties"
               ,"jarFileUrls")
    testCases = ["@Test\npublic void set{method}_test(|args|){{ \n\
    PersistenceUnitInfoBuilder mybuilder = new PersistenceUnitInfoBuilder();\n\
    mybuilder.set{method}(|some args|);\n\
    PersistenceUnitInfoImpl myimpl = mybuilder.build();\n\
    assertEquals(|some args|,myimpl.get{method}());}}\n".format(method=method) for\
    method in methods]
    for case in testCases:
         print(case)

     */
    private PersistenceUnitInfoBuilder mybuilder;

    @BeforeEach
    public void before_each(){
        mybuilder =new PersistenceUnitInfoBuilder("test","test.class.name");
    }

    @Test
    public void setPersistenceUnitName_test(){
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals("test",myimpl.getPersistenceUnitName());
    }

    @Test
    public void setPersistenceProviderClassName_test(){
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals("test.class.name",myimpl.getPersistenceProviderClassName());
    }

    @Test
    public void setPersistenceUnitTransactionType_test(){

        mybuilder.setPersistenceUnitTransactionType(PersistenceUnitTransactionType.RESOURCE_LOCAL);
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals(PersistenceUnitTransactionType.RESOURCE_LOCAL,myimpl.getTransactionType());
    }

    @Test
    public void setJtaDataSource_test(){

        DataSource mockds = mock(DataSource.class);
        mybuilder.setJtaDataSource(mockds);
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals(mockds,myimpl.getJtaDataSource());
        assertNull(myimpl.nonJtaDataSource);
        assertEquals(myimpl.getTransactionType(),PersistenceUnitTransactionType.JTA);
    }

    @Test
    public void setNonJtaDataSource_test(){

        DataSource nonjta = mock(DataSource.class);
        mybuilder.setNonJtaDataSource(nonjta);
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals(nonjta,myimpl.getNonJtaDataSource());
        assertNull(myimpl.jtaDataSource);
        assertEquals(myimpl.getTransactionType(),PersistenceUnitTransactionType.RESOURCE_LOCAL);
    }

    @Test
    public void setMappingFileNames_test(){

        List mappingFileNames = mock(List.class);
        mybuilder.setMappingFileNames(mappingFileNames);
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals(mappingFileNames,myimpl.getMappingFileNames());
    }

    @Test
    public void setManagedClassNames_test(){

        List managedClassNames = mock(List.class);
        mybuilder.setManagedClassNames(managedClassNames);
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals(managedClassNames,myimpl.getManagedClassNames());
    }

    @Test
    public void setSharedCacheMode_test(){

        SharedCacheMode m = SharedCacheMode.UNSPECIFIED;
        mybuilder.setSharedCacheMode(m);
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals(m,myimpl.getSharedCacheMode());
    }

    @Test
    public void setValidationMode_test(){

        ValidationMode m = ValidationMode.AUTO;
        mybuilder.setValidationMode(m);
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals(m,myimpl.getValidationMode());
    }

    @Test
    public void setProperties_test(){

        Properties p = mock(Properties.class);
        mybuilder.setProperties(p);
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals(p,myimpl.getProperties());
    }

    @Test
    public void setJarFileUrls_test(){

        List l = mock(List.class);
        mybuilder.setJarFileUrls(l);
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals(l,myimpl.getJarFileUrls());
    }

    @Test
    public void setPersistenceUnitRootUrl() throws MalformedURLException {

        URL fakeurl = URI.create("file://url").toURL();
        mybuilder.setPersistenceUnitRootUrl(fakeurl);
        PersistenceUnitInfoImpl myimpl = mybuilder.build();
        assertEquals(fakeurl,myimpl.getPersistenceUnitRootUrl());
    }

    Stream<String> getJPAProviderClasses(){
        return Stream.of("org.hibernate.db.jpa.HibernatePersistenceProvider"
                ,"org.eclipse.persistence.db.jpa.PersistenceProvider"
                ,"org.datanucleus.api.db.jpa.PersistenceProviderImpl");
    }

    @TestFactory
    public Stream<DynamicTest> checkForInvalidArgCombinations(){
        List<DynamicTest> tests = new ArrayList<>();
        DataSource mockds = mock(DataSource.class);
        mybuilder = mybuilder.setJtaDataSource(mockds)
                .setPersistenceUnitTransactionType(PersistenceUnitTransactionType.RESOURCE_LOCAL);
        tests.add(DynamicTest.dynamicTest("JTA DataSource and resource local trans"
                ,()->assertThrows(IllegalArgumentException.class, () -> mybuilder.build())));

        mybuilder = mybuilder.setNonJtaDataSource(mockds)
                .setPersistenceUnitTransactionType(PersistenceUnitTransactionType.JTA);
        tests.add(DynamicTest.dynamicTest("Non-JTA DataSource and JTA trans"
        ,()->assertThrows(IllegalArgumentException.class,()->mybuilder.build())));
        return tests.stream();
    }

    @ParameterizedTest
    @MethodSource("getJPAProviderClasses")
    public void getEntityManagerFactory(String providerClass) throws Exception {
        /*Properties jpaProps = new Properties();
        jpaProps.put("javax.persistence.jdbc.driver","test");
        jpaProps.put("javax.persistence.jdbc.url","test");
        jpaProps.put("javax.persistence.jdbc.user","test");
        jpaProps.put("javax.persistence.jdbc.password","test");*/
        /*PersistenceUnitInfoBuilder puBuilder =
                new PersistenceUnitInfoBuilder("test.persistence.unit"
                        ,providerClass);*/

        PersistenceUnitInfo puinfo = mybuilder.build();
        PersistenceProvider provider = (PersistenceProvider)Class.forName(providerClass).newInstance();
        /*
        What follows is probably bad, but I didn't know any other way to do it.
        I wanted to check that the respective JPA providers would load enough to
        report why they can't proceed instead of throwing a NullPointerException
        or something else generic. Setting up connections to data sources looks
        different enough that I won't try to do it ahead of time (end user will
        have to do it herself). So I needed a way to say, "Make sure it doesn't
        throw any unexpected exceptions."
         */
        assertDoesNotThrow(() -> {
                    try {
                        EntityManagerFactory emf = provider.createContainerEntityManagerFactory(puinfo, null);
                    } catch (org.hibernate.HibernateException ex) {
                        System.out.println("Squashed exception from Hibernate. This is expected, see comments in source for details");
                    } catch (org.datanucleus.exceptions.NucleusException ex) {
                        System.out.println("Squashed exception from DataNucleus. This is expected, see comments in source for details");
                    } catch (org.eclipse.persistence.exceptions.EclipseLinkException ex) {
                        System.out.println("Squashed exception from EclipseLink. This is expected, see comments in source for details");
                    }
                }
        );

    }






}
