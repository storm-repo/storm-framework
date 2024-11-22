package st.orm.kotlin.repository;

import st.orm.Templates;
import st.orm.kotlin.model.Owner;
import st.orm.kotlin.model.Pet;
import st.orm.kotlin.model.PetType;
import st.orm.kotlin.model.Visit;
import st.orm.repository.EntityRepository;

import java.util.List;
import java.util.stream.Stream;

import static java.lang.StringTemplate.RAW;
import static st.orm.Templates.alias;
import static st.orm.Templates.table;
import static st.orm.kotlin.KTemplates.where;

public interface PetRepository extends EntityRepository<Pet, Integer> {

    default List<Pet> findAll() {
        return orm().query(RAW."""
                SELECT \{Templates.select(Pet.class)}
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id""")
            .getResultList(Pet.class);
    }

    default Pet findById1(int id) {
        return orm().query(RAW."""
                SELECT \{Templates.select(Pet.class)}
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE p.id = \{id}""")
            .getSingleResult(Pet.class);
    }

    default Pet findById2(int id) {
        return orm().query(RAW."""
                SELECT \{Pet.class}
                FROM \{Pet.class}
                WHERE \{where(Stream.of(id))}""")
            .getSingleResult(Pet.class);
    }

    default Pet findById3(int id) {
        return orm().selectFrom(Pet.class).append(RAW."WHERE \{where(Stream.of(id))}").getSingleResult();
    }

    default Stream<Pet> findByOwnerFirstName(String firstName) {
        return orm().selectFrom(Pet.class).append(RAW."WHERE \{alias(Owner.class)}.first_name = \{firstName}").getResultStream();
    }

    default Stream<Pet> findByOwnerCity(String city) {
        return orm().selectFrom(Pet.class).append(RAW."WHERE \{alias(Owner.class)}.city = \{city}").getResultStream();
    }

    record PetVisitCount(Pet pet, int visitCount) {}

    default Stream<PetVisitCount> petVisitCount() {
        var ORM = orm();
        return ORM
                .selectFrom(Pet.class, PetVisitCount.class, RAW."\{Pet.class}, COUNT(*)")
                .innerJoin(Visit.class).on(Pet.class)
                .append(RAW."GROUP BY \{alias(Pet.class)}.id")
                .getResultStream();
    }
}
