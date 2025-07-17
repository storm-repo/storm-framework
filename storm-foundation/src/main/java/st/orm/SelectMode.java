package st.orm;

/**
 * Specifies the selection mode for query operations.
 */
public enum SelectMode {

    /**
     * Only the primary key fields are selected.
     *
     * <p>This mode returns the minimal data necessary to identify records.</p>
     */
    PK,

    /**
     * Only the fields of the main table are selected, without including nested object hierarchies.
     *
     * <p>This mode is useful if you need the basic attributes of the record without fetching associated records.</p>
     */
    FLAT,

    /**
     * The entire object hierarchy is selected.
     *
     * <p>This mode retrieves the full record along with any nested associations, providing a complete view of the
     * record.</p>
     */
    NESTED
}
