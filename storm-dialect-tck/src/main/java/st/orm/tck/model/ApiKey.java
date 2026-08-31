package st.orm.tck.model;

import static st.orm.GenerationStrategy.NONE;

import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.PK;

@Builder(toBuilder = true)
@DbTable("api_key")
public record ApiKey(
        @PK(generation = NONE) UUID id,
        String name,
        @Nullable UUID externalReference
) implements Entity<UUID> {}
