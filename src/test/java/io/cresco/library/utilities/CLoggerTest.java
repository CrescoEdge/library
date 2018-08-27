package io.cresco.library.utilities;

import io.cresco.library.plugin.Config;
import io.cresco.library.plugin.PluginBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CLoggerTest {
    private CLogger uut;

    public Stream<Arguments> getArgs_formatIssuingClassName(){
        List<String> strings = Arrays.asList(new String[]{null,"","1","12","123"});
        return strings.stream().flatMap( (base) ->
                strings.stream().map( (issuing) ->
                        Arguments.of(base,issuing)));
    }

    @BeforeEach
    public void setup(){
        Map<String,Object> mockConfig = new HashMap<>();
        mockConfig.put("pluginID","TEST-E-TESTER");

        PluginBuilder pb = new PluginBuilder()
                .config(new Config(mockConfig));

        uut = new CLogger(pb,"CLoggerTest","CLoggerTestSetup", CLogger.Level.Trace);
    }

    @ParameterizedTest
    @MethodSource("getArgs_formatIssuingClassName")
    public void formatIssuingClassName_test(String basename, String issuingName){
        String actual = CLogger.formatIssuingClassName(basename, issuingName);
        if(issuingName == null || (issuingName == null && basename == null)){
            assertEquals("",actual);
        }
        else if(basename == null && issuingName != null){
            assertEquals(issuingName,actual);
        }
        else {//both not null
            if(basename.length() < issuingName.length()){
                assertEquals(issuingName.substring(basename.length() +1, issuingName.length()),actual);
            } else {
                assertEquals(issuingName,actual);
            }
        }

    }
}
