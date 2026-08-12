package st.orm.serialization.model

import kotlinx.serialization.Serializable
import st.orm.*

@Serializable
internal data class VetSpecialty(
    @PK(generation = GenerationStrategy.NONE) val id: VetSpecialtyPK, // Implicitly @Inlined
    @FK @Persist(insertable = false) val vet: Vet,
    @FK @Persist(insertable = false) val specialty: Specialty,
) : Entity<VetSpecialtyPK>
