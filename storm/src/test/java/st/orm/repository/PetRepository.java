package st.orm.repository;

import st.orm.Templates;
import st.orm.model.Owner;
import st.orm.model.Pet;
import st.orm.model.PetType;
import st.orm.model.Visit;

import java.util.List;

import static java.lang.StringTemplate.RAW;

public interface PetRepository extends EntityRepository<Pet, Integer> {

    default List<Pet> findAll() {
        return template().query(RAW."""
                SELECT \{Templates.select(Pet.class)}
                FROM \{t(Pet.class, "p")}
                  INNER JOIN \{t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{t(Owner.class, "o")} ON p.owner_id = o.id""")
            .getResultList(Pet.class);
    }

    default Pet findById1(int id) {
        return template().query(RAW."""
                SELECT \{s(Pet.class)}
                FROM \{t(Pet.class, "p")}
                  INNER JOIN \{t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE p.id = \{id}""")
            .getSingleResult(Pet.class);
    }

    default Pet findById2(int id) {
        var ORM = template();
        return ORM.query(RAW."""
                SELECT \{Pet.class}
                FROM \{Pet.class}
                WHERE \{ORM.w(id)}""")
            .getSingleResult(Pet.class);
    }

    default Pet findById3(int id) {
        return template().selectFrom(Pet.class).append(RAW."WHERE \{w(id)}").getSingleResult();
    }

    default List<Pet> findByOwnerFirstName(String firstName) {
        return template().selectFrom(Pet.class).append(RAW."WHERE \{a(Owner.class)}.first_name = \{firstName}").getResultList();
    }

    default List<Pet> findByOwnerCity(String city) {
        return select().append(RAW."WHERE \{Owner.class}.city = \{city}").getResultList();
    }

    default List<Pet> findByOwnerCityQuery(String city) {
        return template().selectFrom(Pet.class).append(RAW."WHERE \{a(Owner.class)}.city = \{city}").getResultList();
    }

    record PetVisitCount(Pet pet, int visitCount) {}

    default List<PetVisitCount> petVisitCount() {
        return template()
                .selectFrom(Pet.class, PetVisitCount.class, RAW."\{Pet.class}, COUNT(*)")
                .innerJoin(Visit.class).on(Pet.class)
                .append(RAW."GROUP BY \{a(Pet.class)}.id")
                .getResultList();
    }
}
