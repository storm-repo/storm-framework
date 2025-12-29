package st.orm.core;

import jakarta.annotation.Nullable;
import st.orm.Converter;
import st.orm.DefaultConverter;

import java.time.LocalDate;

@DefaultConverter
public class AutoConverter implements Converter<LocalDate, RepositoryPreparedStatementIntegrationTest.AutoDate> {
    @Override
    public LocalDate toDatabase(@Nullable RepositoryPreparedStatementIntegrationTest.AutoDate value) {
        return value == null ? null : LocalDate.parse(value.value());
    }

    @Override
    public RepositoryPreparedStatementIntegrationTest.AutoDate fromDatabase(@Nullable LocalDate dbValue) {
        return new RepositoryPreparedStatementIntegrationTest.AutoDate(dbValue == null ? null : dbValue.toString());
    }
}
