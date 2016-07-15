This scenario illustrates how the PostgreSQL JDBC driver behaves when either using the `socketTimeout` connection property or calling `java.sql.Statement.setQueryTimeout()`.

When the `socketTimeout` is reached, the JDBC driver closes the underlying connection; whereas the `queryTimeout` leaves the connection in a usable state.

This basically forbids setting `socketTimeout` when connection pooling is used in a liberal fashion, as the pool is not informed that the connection is closed.  
With "liberal fashion" I mean (using e.g. the [Tomcat JDBC pool](https://tomcat.apache.org/tomcat-8.0-doc/jdbc-pool.html), the default of Spring-Boot): you're not explicitly setting either `testOnReturn` or `testOnBorrow` along with an `validationInterval` less than the `socketTimeout`.
