package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.TemplateString.raw;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.core.template.ORMTemplate;

/**
 * A value interpolated inside a string literal, as in {@code DATE_FORMAT(date, '\0')}, renders as the literal text
 * {@code '?'}. The driver reads that as a quoted question mark rather than a placeholder, while the value is still
 * bound, so every parameter after it binds one position early. Some drivers reject the statement; others run it
 * against silently wrong arguments, so the statement is checked before it is handed over.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class QuotedPlaceholderIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void quotedInterpolationIsRejectedRatherThanShiftingParameters() {
        var orm = ORMTemplate.of(dataSource);
        var e = assertThrows(PersistenceException.class, () ->
                orm.query(raw("SELECT '\0' AS f, c.name FROM city c WHERE c.name = \0", "%Y-%m", "Utrecht"))
                        .getResultList(String.class));
        String message = e.getMessage();
        assertTrue(message.contains("binds 2 positional parameters but exposes 1 placeholders"), message);
        assertTrue(message.contains("string literal"), message);
    }

    @Test
    public void unquotedInterpolationBindsEveryParameterInPosition() {
        var orm = ORMTemplate.of(dataSource);
        // The second parameter drives the predicate, so every row is returned and each carries the first.
        List<String> values = orm.query(raw("SELECT \0 AS f FROM city c WHERE c.id > \0", "%Y-%m", 0))
                .getResultList(String.class);
        assertTrue(values.size() > 1, "the predicate parameter bound in its own position");
        assertEquals("%Y-%m", values.getFirst());
    }
}
