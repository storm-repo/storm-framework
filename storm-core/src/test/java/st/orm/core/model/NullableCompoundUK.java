package st.orm.core.model;

import jakarta.annotation.Nullable;

/**
 * An inline record with nullable constituent fields (String, not @Nonnull, not primitive).
 * Used to test that compound keys with nullable constituents are detected.
 */
public record NullableCompoundUK(
        @Nullable String userId,
        @Nullable String email
) {}
