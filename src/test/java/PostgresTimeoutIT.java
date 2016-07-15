import org.junit.Before;
import org.junit.Test;
import org.postgresql.util.PSQLException;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class PostgresTimeoutIT {

    private String key;

    @Before
    public void setupTable() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS myTable (key VARCHAR, value VARCHAR)");
            key = UUID.randomUUID().toString();
            statement.execute(String.format("INSERT INTO myTable VALUES ('%1$s', '%1$s')", key));
        }
    }

    @Test
    public void databaseAccessWorks() throws Exception {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(String.format("SELECT value FROM myTable WHERE key='%1$s'", key))) {
            assertTrue(resultSet.next());
            assertThat(resultSet.getString(1), is(key));
        }
    }

    @Test
    public void connectionIsClosedIfUsingSocketTimeout() throws Exception {
        CountDownLatch lockedSignal = new CountDownLatch(1);
        Thread thread = lockRow(lockedSignal);
        lockedSignal.await();

        Connection connection = getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN");
            statement.executeQuery(String.format("SELECT value FROM myTable WHERE key='%1$s' FOR UPDATE", key));
            fail("Should run into a timeout!");
        } catch (PSQLException e) {
            assertTrue("If the socketTimeout is applied, the connection should be closed!", connection.isClosed());
        } finally {
            connection.close();
        }

        thread.interrupt();
        thread.join();
    }

    @Test
    public void connectionRemainsOpenIfUsingSocketTimeout() throws Exception {
        CountDownLatch lockedSignal = new CountDownLatch(1);
        Thread thread = lockRow(lockedSignal);
        lockedSignal.await();

        Connection connection = getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN");
            statement.setQueryTimeout(2);
            statement.executeQuery(String.format("SELECT value FROM myTable WHERE key='%1$s' FOR UPDATE", key));
            fail("Should run into a timeout!");
        } catch (PSQLException e) {
            assertFalse("If the queryTimeout is applied, the connection should be left open!", connection.isClosed());
        } finally {
            connection.close();
        }

        thread.interrupt();
        thread.join();
    }

    private Connection getConnection() throws SQLException {
        String postgresPort = System.getProperty("postgres.port", "5432");

        return DriverManager.getConnection(
                "jdbc:postgresql://localhost:" + postgresPort + "/timeout?socketTimeout=4",
                "timeout",
                "password");
    }

    private Thread lockRow(CountDownLatch lockedSignal) {
        Thread thread = new Thread(() -> {
            try (Connection connection = getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("BEGIN");
                statement.executeQuery(String.format("SELECT value FROM myTable WHERE key='%1$s' FOR UPDATE", key));
                lockedSignal.countDown();
                Thread.sleep(6 * 1000);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Locking row with key " + key);
        thread.start();
        return thread;
    }

}
