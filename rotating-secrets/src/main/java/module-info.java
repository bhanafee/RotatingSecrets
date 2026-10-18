/**
 * Credential rotation core: reads secret files and notifies registered {@link
 * com.maybeitssquid.rotatingsecrets.UpdatableCredential updaters} when they change. Depends only on
 * SLF4J; pool adapters live in separate modules.
 */
module com.maybeitssquid.rotatingsecrets {
  requires org.slf4j;

  exports com.maybeitssquid.rotatingsecrets;
}
