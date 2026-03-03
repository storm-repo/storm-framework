package st.orm.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import st.orm.Data;

class ResolverTest {

    record TestData(int id, String userName) implements Data {}

    private RecordField createRecordField(String name, Class<?> type) throws NoSuchMethodException {
        Method method = TestData.class.getMethod(name);
        return new RecordField(TestData.class, name, type, type, false, false, method, List.of());
    }

    private RecordType createRecordType(Class<?> type) {
        return new RecordType(type, type.getDeclaredConstructors()[0], List.of(), List.of());
    }

    @Test
    void columnNameResolverDefaultCamelCaseToSnakeCase() throws NoSuchMethodException {
        RecordField field = createRecordField("userName", String.class);
        String result = ColumnNameResolver.DEFAULT.resolveColumnName(field);
        assertEquals("user_name", result);
    }

    @Test
    void columnNameResolverCamelCaseToSnakeCase() throws NoSuchMethodException {
        ColumnNameResolver resolver = ColumnNameResolver.camelCaseToSnakeCase();
        RecordField field = createRecordField("userName", String.class);
        assertEquals("user_name", resolver.resolveColumnName(field));
    }

    @Test
    void columnNameResolverToUpperCase() throws NoSuchMethodException {
        ColumnNameResolver resolver = ColumnNameResolver.toUpperCase(ColumnNameResolver.DEFAULT);
        RecordField field = createRecordField("userName", String.class);
        assertEquals("USER_NAME", resolver.resolveColumnName(field));
    }

    @Test
    void tableNameResolverDefaultCamelCaseToSnakeCase() {
        RecordType recordType = createRecordType(TestData.class);
        String result = TableNameResolver.DEFAULT.resolveTableName(recordType);
        assertEquals("test_data", result);
    }

    @Test
    void tableNameResolverCamelCaseToSnakeCase() {
        TableNameResolver resolver = TableNameResolver.camelCaseToSnakeCase();
        RecordType recordType = createRecordType(TestData.class);
        assertEquals("test_data", resolver.resolveTableName(recordType));
    }

    @Test
    void tableNameResolverToUpperCase() {
        TableNameResolver resolver = TableNameResolver.toUpperCase(TableNameResolver.DEFAULT);
        RecordType recordType = createRecordType(TestData.class);
        assertEquals("TEST_DATA", resolver.resolveTableName(recordType));
    }

    @Test
    void foreignKeyResolverDefaultCamelCaseToSnakeCase() throws NoSuchMethodException {
        RecordField field = createRecordField("userName", String.class);
        RecordType type = createRecordType(TestData.class);
        String result = ForeignKeyResolver.DEFAULT.resolveColumnName(field, type);
        assertEquals("user_name_id", result);
    }

    @Test
    void foreignKeyResolverCamelCaseToSnakeCase() throws NoSuchMethodException {
        ForeignKeyResolver resolver = ForeignKeyResolver.camelCaseToSnakeCase();
        RecordField field = createRecordField("userName", String.class);
        RecordType type = createRecordType(TestData.class);
        assertEquals("user_name_id", resolver.resolveColumnName(field, type));
    }

    @Test
    void foreignKeyResolverToUpperCase() throws NoSuchMethodException {
        ForeignKeyResolver resolver = ForeignKeyResolver.toUpperCase(ForeignKeyResolver.DEFAULT);
        RecordField field = createRecordField("userName", String.class);
        RecordType type = createRecordType(TestData.class);
        assertEquals("USER_NAME_ID", resolver.resolveColumnName(field, type));
    }
}
