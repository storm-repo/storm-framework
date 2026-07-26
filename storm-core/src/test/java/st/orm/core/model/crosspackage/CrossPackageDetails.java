package st.orm.core.model.crosspackage;

import jakarta.annotation.Nullable;

/**
 * An inline record declared outside the package of the entity that embeds it, so the generated metamodels refer to it
 * by its qualified name.
 */
public record CrossPackageDetails(
        @Nullable String label,
        @Nullable Integer score
) {}
