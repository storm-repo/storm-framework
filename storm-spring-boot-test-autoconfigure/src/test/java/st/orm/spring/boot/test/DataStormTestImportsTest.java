package st.orm.spring.boot.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Guards the slice's imports file. A non-{@code optional:} entry that is absent from the classpath fails
 * the import selector with a raw {@code ClassNotFoundException}, so everything resolved from another
 * artifact must carry the {@code optional:} prefix. The parity check reads the production
 * {@code AutoConfiguration.imports} files and asserts every Storm auto-configuration registered there is
 * importable by the slice, so the slice cannot silently drift from the application's wiring.
 */
class DataStormTestImportsTest {

    private static final String SLICE_IMPORTS =
            "META-INF/spring/st.orm.spring.boot.test.DataStormTest.imports";
    private static final String PRODUCTION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    void everyNonOptionalEntryResolves() {
        for (String entry : sliceEntries()) {
            if (!entry.startsWith("optional:")) {
                assertThat(resolves(entry))
                        .withFailMessage("Non-optional entry %s does not resolve; a consumer without its " +
                                "artifact gets a ClassNotFoundException from the import selector.", entry)
                        .isTrue();
            }
        }
    }

    @Test
    void everyResolvableEntryIsAnAutoConfiguration() throws ClassNotFoundException {
        for (String entry : sliceEntries()) {
            String className = stripOptional(entry);
            if (resolves(className)) {
                Class<?> autoConfiguration = Class.forName(className, false, getClass().getClassLoader());
                assertThat(AnnotatedElementUtils.hasAnnotation(autoConfiguration, AutoConfiguration.class))
                        .withFailMessage("%s is not an auto-configuration.", className)
                        .isTrue();
            }
        }
    }

    @Test
    void entriesAreUnique() {
        List<String> classNames = sliceEntries().stream().map(DataStormTestImportsTest::stripOptional).toList();
        assertThat(classNames).doesNotHaveDuplicates();
    }

    @Test
    void everyProductionStormAutoConfigurationIsImportable() throws IOException {
        List<String> sliceClassNames = sliceEntries().stream()
                .map(DataStormTestImportsTest::stripOptional)
                .toList();
        List<String> productionStormEntries = new ArrayList<>();
        Enumeration<URL> resources = getClass().getClassLoader().getResources(PRODUCTION_IMPORTS);
        while (resources.hasMoreElements()) {
            for (String entry : readEntries(resources.nextElement())) {
                if (entry.startsWith("st.orm.")) {
                    productionStormEntries.add(entry);
                }
            }
        }
        assertThat(productionStormEntries).isNotEmpty();
        assertThat(sliceClassNames).containsAll(productionStormEntries);
    }

    private List<String> sliceEntries() {
        URL resource = getClass().getClassLoader().getResource(SLICE_IMPORTS);
        assertThat(resource).isNotNull();
        return readEntries(resource);
    }

    private static List<String> readEntries(URL resource) {
        List<String> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String entry = line.trim();
                if (!entry.isEmpty() && !entry.startsWith("#")) {
                    entries.add(entry);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }

    private static String stripOptional(String entry) {
        return entry.startsWith("optional:") ? entry.substring("optional:".length()) : entry;
    }

    private boolean resolves(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }
}
