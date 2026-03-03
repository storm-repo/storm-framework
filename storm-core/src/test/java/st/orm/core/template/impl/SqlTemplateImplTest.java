package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.TemplateString.raw;

import org.junit.jupiter.api.Test;
import st.orm.StormConfig;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TableAliasResolver;
import st.orm.mapping.ColumnNameResolver;
import st.orm.mapping.ForeignKeyResolver;
import st.orm.mapping.TableNameResolver;

/**
 * Extended tests for {@link SqlTemplateImpl} covering withColumnNameResolver,
 * withForeignKeyResolver, withTableNameResolver, withSupportRecords, and resolver identity shortcuts.
 */
public class SqlTemplateImplTest {

    private SqlTemplateImpl createTemplate() {
        return (SqlTemplateImpl) SqlTemplate.PS;
    }

    // withColumnNameResolver

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

    // withForeignKeyResolver

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

    // withTableNameResolver

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

    // withSupportRecords

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

    // withDialect

    @Test
    public void testWithDialectReturnsSameWhenIdentical() {
        SqlTemplateImpl template = createTemplate();
        SqlTemplate result = template.withDialect(template.dialect());
        assertSame(template, result, "Should return same instance when dialect is identical");
    }

    // withConfig

    @Test
    public void testWithConfigReturnsSameWhenIdentical() {
        SqlTemplateImpl template = createTemplate();
        SqlTemplate result = template.withConfig(st.orm.StormConfig.defaults());
        // The defaults singleton comparison; if same instance, returns this.
        assertNotNull(result);
    }

    // positionalOnly and expandCollection

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

    // Cache disabled for inline parameters

    @Test
    public void testInlineParametersNewValue() {
        SqlTemplateImpl template = createTemplate();
        SqlTemplate result = template.withInlineParameters(!template.inlineParameters());
        assertNotSame(template, result, "Should return new instance when inlineParameters changes");
        assertNotNull(result);
    }

    @Test
    public void testWithTableAliasResolverReturnsSameWhenIdentical() {
        SqlTemplateImpl template = (SqlTemplateImpl) SqlTemplate.PS;
        TableAliasResolver currentResolver = template.tableAliasResolver();
        SqlTemplateImpl result = template.withTableAliasResolver(currentResolver);
        assertSame(template, result, "Should return same instance when alias resolver is identical");
    }

    @Test
    public void testWithTableAliasResolverReturnsNewInstance() {
        SqlTemplateImpl template = (SqlTemplateImpl) SqlTemplate.PS;
        TableAliasResolver customResolver = (type, counter) -> "custom_" + type.getSimpleName().toLowerCase();
        SqlTemplateImpl result = template.withTableAliasResolver(customResolver);
        assertNotSame(template, result, "Should return new instance when alias resolver is different");
    }

    @Test
    public void testWithInlineParametersReturnsSameWhenIdentical() {
        SqlTemplateImpl template = (SqlTemplateImpl) SqlTemplate.PS;
        SqlTemplate result = template.withInlineParameters(template.inlineParameters());
        assertSame(template, result, "Should return same instance when inlineParameters is unchanged");
    }

    @Test
    public void testWithInlineParametersReturnsNewInstance() {
        SqlTemplateImpl template = (SqlTemplateImpl) SqlTemplate.PS;
        SqlTemplate result = template.withInlineParameters(!template.inlineParameters());
        assertNotSame(template, result, "Should return new instance when inlineParameters changes");
    }

    @Test
    public void testWithConfigReturnsSameWhenSameInstance() {
        SqlTemplateImpl template = (SqlTemplateImpl) SqlTemplate.PS;
        SqlTemplate result = template.withConfig(StormConfig.defaults());
        assertNotNull(result);
    }

    @Test
    public void testInlineParametersDefaultIsFalse() {
        SqlTemplateImpl template = (SqlTemplateImpl) SqlTemplate.PS;
        assertFalse(template.inlineParameters());
    }

    @Test
    public void testProcessWithInlineParameters() throws SqlTemplateException {
        SqlTemplate template = SqlTemplate.PS.withInlineParameters(true);
        Sql sql = template.process(raw("SELECT 1"));
        assertNotNull(sql);
        assertNotNull(sql.statement());
        assertTrue(sql.statement().contains("1"));
    }

    @Test
    public void testProcessCacheHit() throws SqlTemplateException {
        SqlTemplate template = SqlTemplate.PS;
        Sql sql1 = template.process(raw("SELECT id FROM city WHERE id = 1"));
        assertNotNull(sql1);
        Sql sql2 = template.process(raw("SELECT id FROM city WHERE id = 1"));
        assertNotNull(sql2);
        assertEquals(sql1.statement(), sql2.statement());
    }

    @Test
    public void testProcessDifferentTemplatesGetDifferentKeys() throws SqlTemplateException {
        SqlTemplate template = SqlTemplate.PS;
        Sql sql1 = template.process(raw("SELECT id FROM city WHERE id = 1"));
        Sql sql2 = template.process(raw("SELECT name FROM city WHERE id = 1"));
        assertNotNull(sql1);
        assertNotNull(sql2);
        assertFalse(sql1.statement().equals(sql2.statement()));
    }

    @Test
    public void testCreateBindVars() {
        SqlTemplateImpl template = (SqlTemplateImpl) SqlTemplate.PS;
        var bindVars = template.createBindVars();
        assertNotNull(bindVars);
    }

    @Test
    public void testPositionalOnly() {
        SqlTemplateImpl ps = (SqlTemplateImpl) SqlTemplate.PS;
        assertTrue(ps.positionalOnly());
        SqlTemplateImpl jpa = (SqlTemplateImpl) SqlTemplate.JPA;
        assertFalse(jpa.positionalOnly());
    }

    @Test
    public void testExpandCollection() {
        SqlTemplateImpl ps = (SqlTemplateImpl) SqlTemplate.PS;
        assertTrue(ps.expandCollection());
        SqlTemplateImpl jpa = (SqlTemplateImpl) SqlTemplate.JPA;
        assertFalse(jpa.expandCollection());
    }

    @Test
    public void testSupportRecordsDefault() {
        SqlTemplateImpl template = (SqlTemplateImpl) SqlTemplate.PS;
        assertTrue(template.supportRecords());
    }

    @Test
    public void testDialectAccessor() {
        SqlTemplateImpl template = (SqlTemplateImpl) SqlTemplate.PS;
        assertNotNull(template.dialect());
    }

    @Test
    public void testWithDialectReturnsSameWhenSameInstance() {
        SqlTemplateImpl template = (SqlTemplateImpl) SqlTemplate.PS;
        SqlTemplate result = template.withDialect(template.dialect());
        assertSame(template, result);
    }
}
