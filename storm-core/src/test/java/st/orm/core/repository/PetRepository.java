package st.orm.core.repository;

import static st.orm.core.template.TemplateString.raw;
import static st.orm.core.template.Templates.alias;
import static st.orm.core.template.Templates.table;
import static st.orm.core.template.Templates.where;

import java.util.List;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.PetType;
import st.orm.core.model.Pet_;
import st.orm.core.model.Visit;

public interface PetRepository extends EntityRepository<Pet, Integer> {

    default Pet getById1(int id) {
        return orm().query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE p.id = \0""",
            Pet.class, table(Pet.class, "p"), table(PetType.class, "pt"), table(Owner.class, "o"),
            table(City.class, "c"), id))
                .getSingleResult(Pet.class);
    }

    default Pet getById2(int id) {
        return orm().query(raw("""
                SELECT \0
                FROM \0
                WHERE \0""", Pet.class, Pet.class, where(id)))
            .getSingleResult(Pet.class);
    }

    default Pet getById3(int id) {
        return orm().selectFrom(Pet.class).append(raw("WHERE \0", where(id))).getSingleResult();
    }

    default List<Pet> findByOwnerFirstName(String firstName) {
        return orm().selectFrom(Pet.class).append(raw("WHERE \0.first_name = \0",
                alias(Owner.class), firstName)).getResultList();
    }

    default List<Pet> findByOwnerCity(String city) {
        return select().append(raw("WHERE \0.name = \0", City.class, city)).getResultList();
    }

    default List<Pet> findByOwnerCityQuery(String city) {
        return orm().selectFrom(Pet.class).append(raw("WHERE \0.city = \0", alias(Owner.class), city)).getResultList();
    }

    record PetVisitCount(Pet pet, int visitCount) {}

    default List<PetVisitCount> petVisitCount() {
        return orm()
                .selectFrom(Pet.class, PetVisitCount.class, raw("\0, COUNT(*)", Pet.class))
                .innerJoin(Visit.class).on(Pet.class)
                .groupBy(Pet_.id)
                .getResultList();
    }
}
