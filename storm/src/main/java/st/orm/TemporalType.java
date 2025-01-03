package st.orm;

/**
 * Represents the type of a temporal attribute.
 */
public enum TemporalType {

    /** Map as <code>java.sql.Date</code> */
    DATE,

    /** Map as <code>java.sql.Time</code> */
    TIME,

    /** Map as <code>java.sql.Timestamp</code> */
    TIMESTAMP
}