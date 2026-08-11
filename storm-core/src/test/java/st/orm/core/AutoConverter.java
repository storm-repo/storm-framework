package st.orm.core;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import st.orm.Converter;
import st.orm.DefaultConverter;

@DefaultConverter
public class AutoConverter implements Converter<LocalDate, RepositoryPreparedStatementIntegrationTest.AutoDate> {
    @Override
    public LocalDate toDatabase(RepositoryPreparedStatementIntegrationTest.@Nullable AutoDate value) {
        return value == null ? null : LocalDate.parse(value.value());
    }

    @Override
    public RepositoryPreparedStatementIntegrationTest.AutoDate fromDatabase(@Nullable LocalDate dbValue) {
        return new RepositoryPreparedStatementIntegrationTest.AutoDate(dbValue == null ? null : dbValue.toString());
    }
}
