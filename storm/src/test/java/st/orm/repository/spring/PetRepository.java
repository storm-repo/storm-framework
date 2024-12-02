package st.orm.repository.spring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import st.orm.model.Owner;
import st.orm.model.PetType;
import st.orm.model.Pet;

import javax.sql.DataSource;
import java.util.List;

import static java.lang.StringTemplate.RAW;
import static st.orm.Templates.select;
import static st.orm.Templates.table;
import static st.orm.template.PreparedStatementTemplate.ORM;

@Repository
public class PetRepository {

    @Autowired
    private DataSource dataSource;

    public List<Pet> findAll() {
        return ORM(dataSource).query(RAW."""
                SELECT \{select(Pet.class)}
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id""")
        .getResultList(Pet.class);
    }

    public Pet findById(int id) {
        return ORM(dataSource).query(RAW."""
                SELECT \{select(Pet.class)}
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE p.id = \{id}""")
            .getSingleResult(Pet.class);
    }
}
