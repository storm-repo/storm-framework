package st.orm.repository;

import st.orm.model.Owner;
import st.orm.model.Pet;
import st.orm.model.PetType;
import st.orm.model.Visit;

import java.util.List;
import java.util.stream.Stream;

@SuppressWarnings("TrailingWhitespacesInTextBlock")
public interface PetRepository extends EntityRepository<Pet, Integer> {

    default List<Pet> findAll() {
        return template()."""
                SELECT \{this.selectTemplate(Pet.class)}
                FROM \{t(Pet.class, "p")}
                  INNER JOIN \{t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{t(Owner.class, "o")} ON p.owner_id = o.id"""
            .getResultList(Pet.class);
    }

    default Pet findById1(int id) {
        return template()."""
                SELECT \{s(Pet.class)}
                FROM \{t(Pet.class, "p")}
                  INNER JOIN \{t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE p.id = \{id}"""
            .getSingleResult(Pet.class);
    }

    default Pet findById2(int id) {
        var ORM = template();
        return ORM."""
                SELECT \{Pet.class}
                FROM \{Pet.class}
                WHERE \{ORM.w(Stream.of(id))}"""
            .getSingleResult(Pet.class);
    }

    default Pet findById3(int id) {
        return singleResult(template().query(Pet.class)."WHERE \{w(Stream.of(id))}");
    }

    default Stream<Pet> findByOwnerFirstName(String firstName) {
        return template().query(Pet.class)."WHERE \{a(Owner.class)}.first_name = \{firstName}";
    }

    default Stream<Pet> findByOwnerCity(String city) {
        return this."WHERE \{Owner.class}.city = \{city}";
    }

    default Stream<Pet> findByOwnerCityQuery(String city) {
        return template().query(Pet.class)."WHERE \{a(Owner.class)}.city = \{city}";
    }

    record PetVisitCount(Pet pet, int visitCount) {}

    default Stream<PetVisitCount> petVisitCount() {
        var ORM = template();
        return ORM
                .query(Pet.class)
                .selectTemplate(PetVisitCount.class)."\{Pet.class}, COUNT(*)"
                .innerJoin(Visit.class).on(Pet.class)
                ."GROUP BY \{a(Pet.class)}.id";
    }
}
