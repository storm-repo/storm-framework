package st.orm.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NameResolverTest {

    @Test
    void simpleCamelCase() {
        assertEquals("user_name", NameResolver.camelCaseToSnakeCase("userName"));
    }

    @Test
    void singleWord() {
        assertEquals("name", NameResolver.camelCaseToSnakeCase("name"));
    }

    @Test
    void uppercaseLetters() {
        assertEquals("first_name", NameResolver.camelCaseToSnakeCase("firstName"));
    }

    @Test
    void multipleUppercase() {
        assertEquals("my_long_variable_name", NameResolver.camelCaseToSnakeCase("myLongVariableName"));
    }

    @Test
    void singleCharacter() {
        assertEquals("a", NameResolver.camelCaseToSnakeCase("a"));
    }

    @Test
    void startsWithUppercase() {
        assertEquals("user", NameResolver.camelCaseToSnakeCase("User"));
    }

    @Test
    void digitTransitionFromLowercase() {
        assertEquals("survey_terminates_4w", NameResolver.camelCaseToSnakeCase("surveyTerminates4w"));
    }

    @Test
    void digitWithoutLowercasePrefix() {
        assertEquals("a1b", NameResolver.camelCaseToSnakeCase("a1b"));
    }

    @Test
    void consecutiveDigits() {
        assertEquals("abc_123", NameResolver.camelCaseToSnakeCase("abc123"));
    }

    @Test
    void digitAtStartDoesNotInsertUnderscore() {
        // Only 1 char before digit, so no underscore
        assertEquals("a1", NameResolver.camelCaseToSnakeCase("a1"));
    }

    @Test
    void twoLowercaseBeforeDigit() {
        assertEquals("ab_1", NameResolver.camelCaseToSnakeCase("ab1"));
    }
}
