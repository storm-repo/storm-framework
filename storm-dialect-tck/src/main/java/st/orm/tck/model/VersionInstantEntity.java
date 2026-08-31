package st.orm.tck.model;

import java.time.Instant;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.PK;
import st.orm.Version;

@Builder(toBuilder = true)
@DbTable("version_instant_entity")
public record VersionInstantEntity(
        @PK Integer id,
        String name,
        @Version @Nullable Instant version
) implements Entity<Integer> {}
