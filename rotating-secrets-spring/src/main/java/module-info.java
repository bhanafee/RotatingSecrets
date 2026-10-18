/**
 * Spring configuration for credential rotation. Creates the {@link
 * com.maybeitssquid.rotatingsecrets.CredentialsProviderService} and registers every {@code
 * UpdatableCredential<String>} bean in the context.
 */
@SuppressWarnings("requires-transitive-automatic")
module com.maybeitssquid.rotatingsecrets.spring {
  requires transitive com.maybeitssquid.rotatingsecrets;
  requires transitive spring.context;
  requires spring.beans;

  exports com.maybeitssquid.rotatingsecrets.spring;
  // Spring reflects on the @Configuration class and its @Value parameters.
  opens com.maybeitssquid.rotatingsecrets.spring to spring.core, spring.beans, spring.context;
}
