/**
 * HikariCP adapter for credential rotation. Spring configuration in {@code hikari} reads the
 * {@code spring.datasource.hikari.*} properties.
 */
@SuppressWarnings("requires-transitive-automatic")
module com.maybeitssquid.rotatingsecrets.hikari {
  requires transitive com.maybeitssquid.rotatingsecrets;
  requires transitive com.zaxxer.hikari;
  requires transitive java.sql;
  requires transitive spring.context;
  requires spring.beans;

  exports com.maybeitssquid.rotatingsecrets.hikari;
  // Spring reflects on the @Configuration classes and their @Value fields.
  opens com.maybeitssquid.rotatingsecrets.hikari to spring.core, spring.beans, spring.context;
}
