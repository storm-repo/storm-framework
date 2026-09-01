package st.orm.tck.model;

import static st.orm.GenerationStrategy.SEQUENCE;

import lombok.Builder;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.PK;

/**
 * Sequence-backed key. The sequence is named explicitly because the dialects that support sequences at all expect to
 * be told which one; a dialect without named sequences skips the tests that need the table through
 * {@code AbstractEntityRepositoryConformanceTest#supportsSequences()}.
 */
@Builder(toBuilder = true)
@DbTable("seq_entity")
public record SeqEntity(
        @PK(generation = SEQUENCE, sequence = "seq_entity_id_seq") Integer id,
        String name
) implements Entity<Integer> {}
