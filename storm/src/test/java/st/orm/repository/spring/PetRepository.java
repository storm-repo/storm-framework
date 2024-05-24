package st.orm.repository.spring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import st.orm.model.Owner;
import st.orm.model.PetType;
import st.orm.model.Pet;

import javax.sql.DataSource;
import java.util.List;

import static st.orm.template.PreparedStatementTemplate.ORM;

@SuppressWarnings("TrailingWhitespacesInTextBlock")
@Repository
public class PetRepository {

    @Autowired
    private DataSource dataSource;

    public List<Pet> findAll() {
        var ORM = ORM(dataSource);
        return ORM."""
                SELECT \{ORM.s(Pet.class)}
                FROM \{ORM.t(Pet.class, "p")}
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id"""
        .getResultList(Pet.class);
    }

    public Pet findById(int id) {
        var ORM = ORM(dataSource);
        return ORM."""
                SELECT \{ORM.s(Pet.class)}
                FROM \{ORM.t(Pet.class, "p")}
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE p.id = \{id}"""
            .getSingleResult(Pet.class);
    }
}
