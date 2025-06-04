package st.orm.template.impl;

import st.orm.template.SqlTemplateException;

public class UncheckedSqlTemplateException extends RuntimeException {

    private final SqlTemplateException cause;
    
    public UncheckedSqlTemplateException(SqlTemplateException e) {
        super(e);
        this.cause = e;
    }

    @Override
    public SqlTemplateException getCause() {
        return cause;
    }
}
