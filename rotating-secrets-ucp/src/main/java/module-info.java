/**
 * Oracle Universal Connection Pool adapter for credential rotation. Spring configuration in {@code
 * ucp} reads the {@code spring.datasource.ucp.*} properties.
 */
@SuppressWarnings("requires-transitive-automatic")
module com.maybeitssquid.rotatingsecrets.ucp {
  requires transitive com.maybeitssquid.rotatingsecrets;
  requires transitive com.oracle.database.ucp;
  requires transitive java.sql;
  requires transitive spring.context;
  requires spring.beans;

  exports com.maybeitssquid.rotatingsecrets.ucp;
  // Spring reflects on the @Configuration classes and their @Value fields.
  opens com.maybeitssquid.rotatingsecrets.ucp to spring.core, spring.beans, spring.context;
}
