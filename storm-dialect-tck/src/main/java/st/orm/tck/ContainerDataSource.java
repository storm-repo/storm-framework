package st.orm.tck;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * A data source over {@link DriverManager}, for a conformance suite that starts its own container and hands the
 * connection details to {@code @StormTest} through a static {@code dataSource()} method.
 */
public final class ContainerDataSource implements DataSource {

    private final String url;
    private final Properties properties = new Properties();

    private ContainerDataSource(String url, String username, String password) {
        this.url = url;
        properties.setProperty("user", username);
        properties.setProperty("password", password);
    }

    public static DataSource of(String url, String username, String password) {
        return new ContainerDataSource(url, username, password);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, properties);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    @Override
    public PrintWriter getLogWriter() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setLoginTimeout(int seconds) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName() + ".");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }
}
