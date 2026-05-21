using System.Runtime.InteropServices;
using MaybeItsSquid.RotatingSecrets.Configuration;
using MaybeItsSquid.RotatingSecrets.Core;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace MaybeItsSquid.RotatingSecrets.Services;

/// <summary>
/// Reads credentials from Kubernetes-mounted secret files.
/// </summary>
public class FileSecretReader : ISecretFileReader
{
    private readonly RotatingSecretsOptions _options;
    private readonly ILogger<FileSecretReader> _logger;

    /// <summary>
    /// Initializes a new instance of the <see cref="FileSecretReader"/> class.
    /// </summary>
    /// <param name="options">Configuration options.</param>
    /// <param name="logger">Logger instance.</param>
    public FileSecretReader(
        IOptions<RotatingSecretsOptions> options,
        ILogger<FileSecretReader> logger)
    {
        _options = options.Value;
        _logger = logger;
    }

    /// <inheritdoc />
    public DatabaseCredentials? ReadCredentials()
    {
        if (!SecretsExist())
        {
            _logger.LogDebug("Secret files not found at {Path}", _options.SecretsPath);
            return null;
        }

        try
        {
            var username = File.ReadAllText(_options.UsernameFilePath).Trim();
            var password = File.ReadAllText(_options.PasswordFilePath).Trim();

            return new DatabaseCredentials(username, password);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            _logger.LogError(ex, "Failed to read credentials from {Path}", _options.SecretsPath);
            throw new CredentialRotationException(
                $"Failed to read credentials from {_options.SecretsPath}", ex);
        }
    }

    /// <inheritdoc />
    public async Task<DatabaseCredentials?> ReadCredentialsAsync(CancellationToken cancellationToken = default)
    {
        if (!SecretsExist())
        {
            _logger.LogDebug("Secret files not found at {Path}", _options.SecretsPath);
            return null;
        }

        try
        {
            var usernameTask = File.ReadAllTextAsync(_options.UsernameFilePath, cancellationToken);
            var passwordTask = File.ReadAllTextAsync(_options.PasswordFilePath, cancellationToken);

            await Task.WhenAll(usernameTask, passwordTask);

            var username = (await usernameTask).Trim();
            var password = (await passwordTask).Trim();

            return new DatabaseCredentials(username, password);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or OperationCanceledException)
        {
            if (ex is OperationCanceledException)
            {
                throw;
            }

            _logger.LogError(ex, "Failed to read credentials from {Path}", _options.SecretsPath);
            throw new CredentialRotationException(
                $"Failed to read credentials from {_options.SecretsPath}", ex);
        }
    }

    /// <inheritdoc />
    public bool SecretsExist()
    {
        return File.Exists(_options.UsernameFilePath) &&
               File.Exists(_options.PasswordFilePath);
    }

    /// <inheritdoc />
    public void ValidatePermissions()
    {
        if (!_options.ValidateFilePermissions)
        {
            return;
        }

        // Only validate on Unix-like systems
        if (!RuntimeInformation.IsOSPlatform(OSPlatform.Linux) &&
            !RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
        {
            _logger.LogDebug("Skipping file permission validation on non-Unix platform");
            return;
        }

        if (!SecretsExist())
        {
            return;
        }

        ValidateFilePermission(_options.UsernameFilePath);
        ValidateFilePermission(_options.PasswordFilePath);
    }

    private void ValidateFilePermission(string filePath)
    {
        try
        {
            var fileInfo = new FileInfo(filePath);
            var unixFileMode = fileInfo.UnixFileMode;

            // Check if file is world-readable (others have read permission)
            if ((unixFileMode & UnixFileMode.OtherRead) != 0)
            {
                _logger.LogWarning(
                    "Secret file {Path} is world-readable. Consider restricting permissions to prevent unauthorized access",
                    filePath);
            }

            // Check if file is world-writable (others have write permission)
            if ((unixFileMode & UnixFileMode.OtherWrite) != 0)
            {
                _logger.LogWarning(
                    "Secret file {Path} is world-writable. This is a security risk",
                    filePath);
            }
        }
        catch (Exception ex) when (ex is PlatformNotSupportedException or IOException)
        {
            _logger.LogDebug(ex, "Could not validate permissions for {Path}", filePath);
        }
    }
}
