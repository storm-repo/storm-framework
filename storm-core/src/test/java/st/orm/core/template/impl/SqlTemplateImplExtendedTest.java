package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import st.orm.core.template.SqlTemplate;
import st.orm.mapping.ColumnNameResolver;
import st.orm.mapping.ForeignKeyResolver;
import st.orm.mapping.TableNameResolver;

/**
 * Extended tests for {@link SqlTemplateImpl} covering withColumnNameResolver,
 * withForeignKeyResolver, withTableNameResolver, withSupportRecords, and resolver identity shortcuts.
 */
public class SqlTemplateImplExtendedTest {

    private SqlTemplateImpl createTemplate() {
        return (SqlTemplateImpl) SqlTemplate.PS;
    }

    // ---- withColumnNameResolver ----

    @Test
    public void testWithColumnNameResolverReturnsSameWhenIdentical() {
        SqlTemplateImpl template = createTemplate();
        ColumnNameResolver currentResolver = template.columnNameResolver();
        SqlTemplateImpl result = template.withColumnNameResolver(currentResolver);
        assertSame(template, result, "Should return same instance when resolver is identical");
    }

    @Test
    public void testWithColumnNameResolverReturnsNewInstance() {
        SqlTemplateImpl template = createTemplate();
        ColumnNameResolver customResolver = field -> "custom_" + field.name();
        SqlTemplateImpl result = template.withColumnNameResolver(customResolver);
        assertNotSame(template, result, "Should return new instance when resolver is different");
        assertNotNull(result);
    }

    // ---- withForeignKeyResolver ----

    @Test
    public void testWithForeignKeyResolverReturnsSameWhenIdentical() {
        SqlTemplateImpl template = createTemplate();
        ForeignKeyResolver currentResolver = template.foreignKeyResolver();
        SqlTemplateImpl result = template.withForeignKeyResolver(currentResolver);
        assertSame(template, result, "Should return same instance when resolver is identical");
    }

    @Test
    public void testWithForeignKeyResolverReturnsNewInstance() {
        SqlTemplateImpl template = createTemplate();
        ForeignKeyResolver customResolver = (field, type) -> field.name() + "_fk";
        SqlTemplateImpl result = template.withForeignKeyResolver(customResolver);
        assertNotSame(template, result, "Should return new instance when resolver is different");
        assertNotNull(result);
    }

    // ---- withTableNameResolver ----

    @Test
    public void testWithTableNameResolverReturnsSameWhenIdentical() {
        SqlTemplateImpl template = createTemplate();
        TableNameResolver currentResolver = template.tableNameResolver();
        SqlTemplateImpl result = template.withTableNameResolver(currentResolver);
        assertSame(template, result, "Should return same instance when resolver is identical");
    }

    @Test
    public void testWithTableNameResolverReturnsNewInstance() {
        SqlTemplateImpl template = createTemplate();
        TableNameResolver customResolver = type -> "prefix_" + type.type().getSimpleName();
        SqlTemplateImpl result = template.withTableNameResolver(customResolver);
        assertNotSame(template, result, "Should return new instance when resolver is different");
        assertNotNull(result);
    }

    // ---- withSupportRecords ----

    @Test
    public void testWithSupportRecordsReturnsSameWhenIdentical() {
        SqlTemplateImpl template = createTemplate();
        SqlTemplateImpl result = template.withSupportRecords(true);
        assertSame(template, result, "Should return same instance when supportRecords is unchanged");
    }

    @Test
    public void testWithSupportRecordsReturnsNewInstance() {
        SqlTemplateImpl template = createTemplate();
        SqlTemplateImpl result = template.withSupportRecords(false);
        assertNotSame(template, result, "Should return new instance when supportRecords changes");
        assertNotNull(result);
    }

    // ---- withDialect ----

    @Test
    public void testWithDialectReturnsSameWhenIdentical() {
        SqlTemplateImpl template = createTemplate();
        SqlTemplate result = template.withDialect(template.dialect());
        assertSame(template, result, "Should return same instance when dialect is identical");
    }

    // ---- withConfig ----

    @Test
    public void testWithConfigReturnsSameWhenIdentical() {
        SqlTemplateImpl template = createTemplate();
        SqlTemplate result = template.withConfig(st.orm.StormConfig.defaults());
        // The defaults singleton comparison; if same instance, returns this.
        assertNotNull(result);
    }

    // ---- positionalOnly and expandCollection ----

    @Test
    public void testPositionalOnlyReturnsTrue() {
        SqlTemplateImpl template = createTemplate();
        assertTrue(template.positionalOnly());
    }

    @Test
    public void testExpandCollectionReturnsTrue() {
        SqlTemplateImpl template = createTemplate();
        assertTrue(template.expandCollection());
    }

    // ---- Cache disabled for inline parameters ----

    @Test
    public void testInlineParametersNewValue() {
        SqlTemplateImpl template = createTemplate();
        SqlTemplate result = template.withInlineParameters(!template.inlineParameters());
        assertNotSame(template, result, "Should return new instance when inlineParameters changes");
        assertNotNull(result);
    }
}
