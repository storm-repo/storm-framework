package st.orm;

import jakarta.annotation.Nonnull;

import java.util.stream.Stream;

/**
 * Batch callback interface.
 *
 * @param <T> input type.
 */
@FunctionalInterface
public interface BatchCallback<T> {

    /**
     * Process the given stream.
     *
     * @param batch batch stream to process.
     */
    void process(@Nonnull Stream<T> batch);
}
