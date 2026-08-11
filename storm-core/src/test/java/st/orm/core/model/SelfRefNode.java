package st.orm.core.model;

import org.jspecify.annotations.Nullable;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Ref;

@DbTable("self_ref_node")
public record SelfRefNode(
        @PK Integer id,
        @Nullable String name,
        @Nullable @FK Ref<SelfRefNode> parent
) implements Entity<Integer> {
}
