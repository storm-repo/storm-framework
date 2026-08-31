package st.orm.tck.model;

import lombok.Builder;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.PK;
import st.orm.Version;

@Builder(toBuilder = true)
@DbTable("version_long_entity")
public record VersionLongEntity(
        @PK Integer id,
        String name,
        @Version long version
) implements Entity<Integer> {}
