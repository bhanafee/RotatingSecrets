package com.maybeitssquid.rotatingsecrets;

/**
 * Exception thrown when credential rotation fails.
 *
 * <p>This exception wraps underlying errors that occur during the credential
 * rotation process, such as failures to update connection pool credentials
 * or refresh pool connections.</p>
 *
 * @see CredentialsProviderService
 * @see UpdatableCredential
 */
public class CredentialRotationException extends RuntimeException {

    /**
     * Creates a new credential rotation exception with the specified message and cause.
     *
     * @param message detailed description of the rotation failure
     * @param cause   the underlying exception that caused the failure
     */
    public CredentialRotationException(String message, Throwable cause) {
        super(message, cause);
    }
}
