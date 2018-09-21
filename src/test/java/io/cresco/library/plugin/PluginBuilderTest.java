package io.cresco.library.plugin;

import io.cresco.library.db.results.PluginInventoryResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;



@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PluginBuilderTest {
    @Mock
    private PluginBuilder testBuilder;

    @BeforeEach
    private void beforeEachTest() {
        initMocks(this);
    }

    private static Stream<String> getPluginInventoryTestArgs(){
        return Stream.of(".","invalidPath",null);
    }
    @ParameterizedTest
    @MethodSource("getPluginInventoryTestArgs")
    void getPluginInventoryTest(String repoPath){
        when(testBuilder.getPluginInventory(any())).thenCallRealMethod();
        when(testBuilder.getJarMD5(any())).thenReturn("some_md5");
        when(testBuilder.getPluginVersion(any())).thenReturn("some_version");
        when(testBuilder.getPluginName(any())).thenReturn("some_plugin_name");
        Optional<PluginInventoryResult> actual = testBuilder.getPluginInventory(repoPath);
        /*The order of the expressions matters in the conditional below. If we don't have the short
        circuit in there for the null case, our test throws a NullPointerException
         */
        if(repoPath != null && repoPath.equals(".")){
            assertNotEquals(Optional.empty(),actual);
        } else {
            assertEquals(Optional.empty(),actual);
        }
    }
}
