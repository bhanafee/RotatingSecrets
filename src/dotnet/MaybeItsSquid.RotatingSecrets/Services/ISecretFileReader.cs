using MaybeItsSquid.RotatingSecrets.Core;

namespace MaybeItsSquid.RotatingSecrets.Services;

/// <summary>
/// Abstraction for reading credential files from the filesystem.
/// </summary>
public interface ISecretFileReader
{
    /// <summary>
    /// Reads credentials from the configured secret files.
    /// </summary>
    /// <returns>The credentials read from files, or null if files don't exist.</returns>
    DatabaseCredentials? ReadCredentials();

    /// <summary>
    /// Reads credentials from the configured secret files asynchronously.
    /// </summary>
    /// <param name="cancellationToken">Cancellation token.</param>
    /// <returns>The credentials read from files, or null if files don't exist.</returns>
    Task<DatabaseCredentials?> ReadCredentialsAsync(CancellationToken cancellationToken = default);

    /// <summary>
    /// Checks if the secret files exist and are readable.
    /// </summary>
    /// <returns>True if both username and password files exist.</returns>
    bool SecretsExist();

    /// <summary>
    /// Validates file permissions and logs warnings if files are world-readable.
    /// </summary>
    void ValidatePermissions();
}
