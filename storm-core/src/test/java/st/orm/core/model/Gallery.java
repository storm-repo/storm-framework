package st.orm.core.model;

import jakarta.annotation.Nonnull;
import java.util.List;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.Json;
import st.orm.PK;

/**
 * Holds a converted column: the tags live in a single JSON column, so the column is text while the record holds a
 * list. The metamodel addresses the column by the type {@link st.orm.MetamodelType} names on {@link Json} and keeps
 * the declared type for value extraction.
 */
@DbTable("city")
public record Gallery(
        @PK Integer id,
        @Nonnull @Json List<String> tags
) implements Entity<Integer> {}
