package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import st.orm.PersistenceException;
import st.orm.Query;
import st.orm.kotlin.KPreparedQuery;
import st.orm.kotlin.KQuery;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;

import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class KQueryImpl implements KQuery {
    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final Query query;

    public KQueryImpl(@Nonnull Query query) {
        this.query = requireNonNull(query, "query");
    }

    /**
     * Prepares the query for execution.
     *
     * <p>Queries are normally constructed in a lazy fashion, unlike prepared queries which are constructed eagerly.
     * Prepared queries allow the use of bind variables and enable reading generated keys after row insertion.</p>
     *
     * <p>Note that the prepared query must be closed after usage to prevent resource leaks. As the prepared query is
     * AutoCloseable, it is recommended to use it within a try-with-resources block.</p>
     *
     * @return the prepared query.
     * @throws PersistenceException if the query preparation fails.
     */
    @Override
    public KPreparedQuery prepare() {
        return new KPreparedQueryImpl(query.prepare());
    }

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row corresponds to the
     * order of values in the row array.</p>
     *
     * <p>The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of records.</p>
     *
     * <p>Note that calling this method does trigger the execution of the underlying query, so it should only be invoked
     * when the query is intended to run. Since the stream holds resources open while in use, it must be closed after
     * usage to prevent resource leaks.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    @Override
    public Stream<Object[]> getResultStream() {
        return query.getResultStream();
    }

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments of the specified {@code type}.</p>
     *
     * <p>The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of records.</p>
     *
     * <p>Note that calling this method does trigger the execution of the underlying query, so it should only be invoked
     * when the query is intended to run. Since the stream holds resources open while in use, it must be closed after
     * usage to prevent resource leaks.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    @Override
    public <T> Stream<T> getResultStream(@Nonnull KClass<T> type) {
        //noinspection unchecked
        return query.getResultStream((Class<T>) REFLECTION.getType(type));
    }

    /**
     * Returns true if the query is version aware, false otherwise.
     *
     * @return true if the query is version aware, false otherwise.
     */
    @Override
    public boolean isVersionAware() {
        return query.isVersionAware();
    }

    /**
     * Execute a command, such as an INSERT, UPDATE or DELETE statement.
     *
     * @return the number of rows impacted as result of the statement.
     * @throws PersistenceException if the statement fails.
     */
    @Override
    public int executeUpdate() {
        return query.executeUpdate();
    }

    /**
     * Execute a batch of commands.
     *
     * @throws PersistenceException if the batch fails.
     * @return an array of update counts containing one element for each command in the batch. The elements of the
     * array are ordered according to the order in which commands were added to the batch, following
     * {@code Statement.executeBatch} semantics.
     */
    @Override
    public int[] executeBatch() {
        return query.executeBatch();
    }
}
