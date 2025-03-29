package st.orm.repository;

import st.orm.Templates;
import st.orm.model.City;
import st.orm.model.Owner;
import st.orm.model.Pet;
import st.orm.model.PetType;
import st.orm.model.Pet_;
import st.orm.model.Visit;

import java.util.List;

import static java.lang.StringTemplate.RAW;
import static st.orm.Templates.alias;
import static st.orm.Templates.table;
import static st.orm.Templates.where;

public interface PetRepository extends EntityRepository<Pet, Integer> {

    default List<Pet> findAll() {
        return orm().query(RAW."""
                SELECT \{Templates.select(Pet.class)})
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id""")
            .getResultList(Pet.class);
    }

    default Pet findById1(int id) {
        return orm().query(RAW."""
                SELECT \{Pet.class}
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                  LEFT OUTER JOIN \{table(City.class, "c")} ON o.city_id = c.id
                WHERE p.id = \{id}""")
            .getSingleResult(Pet.class);
    }

    default Pet findById2(int id) {
        return orm().query(RAW."""
                SELECT \{Pet.class}
                FROM \{Pet.class}
                WHERE \{where(id)}""")
            .getSingleResult(Pet.class);
    }

    default Pet findById3(int id) {
        return orm().selectFrom(Pet.class).append(RAW."WHERE \{where(id)}").getSingleResult();
    }

    default List<Pet> findByOwnerFirstName(String firstName) {
        return orm().selectFrom(Pet.class).append(RAW."WHERE \{alias(Owner.class)}.first_name = \{firstName}").getResultList();
    }

    default List<Pet> findByOwnerCity(String city) {
        return select().append(RAW."WHERE \{City.class}.name = \{city}").getResultList();
    }

    default List<Pet> findByOwnerCityQuery(String city) {
        return orm().selectFrom(Pet.class).append(RAW."WHERE \{alias(Owner.class)}.city = \{city}").getResultList();
    }

    record PetVisitCount(Pet pet, int visitCount) {}

    default List<PetVisitCount> petVisitCount() {
        return orm()
                .selectFrom(Pet.class, PetVisitCount.class, RAW."\{Pet.class}, COUNT(*)")
                .innerJoin(Visit.class).on(Pet.class)
                .groupBy(Pet_.id)
                .getResultList();
    }
}
