package st.orm.tck;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a dialect is expected to generate for one {@link Statement}. The statement text is always asserted; the
 * generated keys and the presence of bind variables are asserted only when the dialect states them, because not every
 * test cares and the two differ between dialects that return keys from a {@code RETURNING} clause and dialects that
 * report them through the driver.
 */
public record Expected(String statement,
                       @Nullable List<String> generatedKeys,
                       @Nullable Boolean bindVariables) {

    /** Pins the statement text alone. */
    public static Expected sql(String statement) {
        return new Expected(statement, null, null);
    }

    /** Also pins the generated keys the dialect reports; pass no arguments for a dialect that reports none. */
    public Expected keys(String... keys) {
        return new Expected(statement, List.of(keys), bindVariables);
    }

    /** Also pins whether the statement carries bind variables. */
    public Expected bound(boolean present) {
        return new Expected(statement, generatedKeys, present);
    }
}
