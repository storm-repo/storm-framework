package st.orm.tck;

/**
 * Names a statement the conformance suite pins. The suite asserts the behavior on every dialect; the SQL it generates
 * differs per dialect, so each dialect supplies the text through
 * {@code AbstractEntityRepositoryConformanceTest#expectedSql}. Naming the statements rather than inlining the strings
 * keeps a dialect's expectations in one table, where a missing entry fails
 * {@code AbstractEntityRepositoryConformanceTest#everyPinnedStatementHasAnExpectation} instead of going unnoticed.
 *
 * <p>A constant is added here as its test moves into the shared suite, so this enum tracks what the suite covers
 * rather than what it aims to cover. Tests whose assertions hold on every dialect need no constant at all.
 */
public enum Statement {
    INSERT_AND_FETCH,
    INSERT_AND_FETCH_BATCH,
    INSERT_AND_FETCH_COMPOUND_PK,
    INSERT_AND_FETCH_BATCH_COMPOUND_PK,
    INSERT_AND_FETCH_INLINE,
    INSERT_AND_FETCH_INLINE_BATCH,
    SELECT_LIMIT,
    SELECT_OFFSET,
    SELECT_LIMIT_OFFSET,
    UPDATE_AND_FETCH_INLINE_VERSION,
    UPDATE_AND_FETCH_INLINE_VERSION_BATCH,
    UPSERT,
    UPSERT_BATCH,
    UPSERT_AND_FETCH_BATCH,
    UPSERT_AND_FETCH_INLINE_VERSION,
    UPSERT_INLINE_VERSION_BATCH,
    UPSERT_AND_FETCH_BATCH_EXISTING_COMPOUND_PK,
    UPSERT_AND_FETCH_BATCH_NEW_COMPOUND_PK,
    UPSERT_NON_AUTO_GENERATED,
    UPSERT_AND_FETCH_NON_AUTO_GENERATED,
    UPSERT_AND_FETCH_NON_AUTO_GENERATED_BATCH,
    UPSERT_INLINE_VERSION
}
