package st.orm.core.repository.spring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.PetType;
import st.orm.core.model.Pet;

import javax.sql.DataSource;
import java.util.List;

import static st.orm.core.Templates.select;
import static st.orm.core.Templates.table;
import static st.orm.core.template.PreparedStatementTemplate.ORM;
import static st.orm.core.template.TemplateString.raw;

@Repository
public class PetRepository {

    @Autowired
    private DataSource dataSource;

    public List<Pet> findAll() {
        return ORM(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id""",
            select(Pet.class), table(Pet.class, "p"), table(PetType.class, "pt"),
            table(Owner.class, "o"), table(City.class, "c")))
                .getResultList(Pet.class);
    }

    public Pet findById(int id) {
        return ORM(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE p.id = \0""",
            select(Pet.class), table(Pet.class, "p"), table(PetType.class, "pt"),
            table(Owner.class, "o"), table(City.class, "c"), id))
                .getSingleResult(Pet.class);
    }
}
