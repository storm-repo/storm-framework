package st.orm.config;

import jakarta.annotation.Nonnull;

public interface TemplateDecorator {

    /**
     * Returns a new prepared statement template with the specified table name resolver.
     *
     * @param tableNameResolver the table name resolver.
     * @return a new prepared statement template.
     */
    TemplateDecorator withTableNameResolver(@Nonnull TableNameResolver tableNameResolver);

    /**
     * Returns a new prepared statement template with the specified column name resolver.
     *
     * @param columnNameResolver the column name resolver.
     * @return a new prepared statement template.
     */
    TemplateDecorator withColumnNameResolver(@Nonnull ColumnNameResolver columnNameResolver);

    /**
     * Returns a new prepared statement template with the specified foreign key resolver.
     *
     * @param foreignKeyResolver the foreign key resolver.
     * @return a new prepared statement template.
     */
    TemplateDecorator withForeignKeyResolver(@Nonnull ForeignKeyResolver foreignKeyResolver);
}
