package st.orm.spring.boot.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * Verifies that the {@code properties} attribute reaches the environment.
 */
@DataStormTest(properties = "storm.validation.schema-mode=none")
class DataStormTestPropertiesTest {

    @Autowired
    private Environment environment;

    @Test
    void annotationPropertiesAreApplied() {
        assertThat(environment.getProperty("storm.validation.schema-mode")).isEqualTo("none");
    }
}
