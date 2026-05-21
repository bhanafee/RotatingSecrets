namespace MaybeItsSquid.RotatingSecrets.Core;

/// <summary>
/// Observer interface for receiving credential rotation notifications.
/// Implementations must be thread-safe as notifications occur from background threads.
/// </summary>
/// <remarks>
/// This is the .NET equivalent of the Java UpdatableCredential interface.
/// Implementations should handle credential updates atomically and trigger
/// any necessary connection pool operations (e.g., ClearPool).
/// </remarks>
public interface ICredentialUpdatable
{
    /// <summary>
    /// Called when credentials have been rotated.
    /// </summary>
    /// <param name="credentials">The new credentials.</param>
    void OnCredentialsRotated(DatabaseCredentials credentials);
}
