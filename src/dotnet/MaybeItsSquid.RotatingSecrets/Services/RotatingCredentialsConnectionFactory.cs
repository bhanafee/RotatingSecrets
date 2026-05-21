using System.Data.Common;
using MaybeItsSquid.RotatingSecrets.Configuration;
using MaybeItsSquid.RotatingSecrets.Core;
using MaybeItsSquid.RotatingSecrets.Providers;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace MaybeItsSquid.RotatingSecrets.Services;

/// <summary>
/// Connection factory that automatically updates connections when credentials rotate.
/// Implements both <see cref="IDbConnectionFactory"/> for creating connections and
/// <see cref="ICredentialUpdatable"/> for receiving rotation notifications.
/// </summary>
/// <remarks>
/// This is the .NET equivalent of HikariCP's soft eviction pattern.
/// When credentials rotate, we:
/// 1. Build a new connection string with the new credentials
/// 2. Clear the old connection pool (ADO.NET pools are keyed by connection string)
/// 3. New connections automatically use the new connection string
/// </remarks>
public class RotatingCredentialsConnectionFactory : IDbConnectionFactory, ICredentialUpdatable
{
    private readonly IConnectionProvider _connectionProvider;
    private readonly RotatingSecretsOptions _options;
    private readonly ILogger<RotatingCredentialsConnectionFactory> _logger;
    private readonly object _lock = new();

    private volatile string _currentConnectionString;
    private volatile DatabaseCredentials? _currentCredentials;

    /// <summary>
    /// Initializes a new instance of the <see cref="RotatingCredentialsConnectionFactory"/> class.
    /// </summary>
    /// <param name="connectionProvider">The database-specific connection provider.</param>
    /// <param name="options">Configuration options.</param>
    /// <param name="logger">Logger instance.</param>
    public RotatingCredentialsConnectionFactory(
        IConnectionProvider connectionProvider,
        IOptions<RotatingSecretsOptions> options,
        ILogger<RotatingCredentialsConnectionFactory> logger)
    {
        _connectionProvider = connectionProvider;
        _options = options.Value;
        _logger = logger;

        // Initialize with empty connection string (will be set when credentials arrive)
        _currentConnectionString = string.Empty;
    }

    /// <inheritdoc />
    public void OnCredentialsRotated(DatabaseCredentials credentials)
    {
        lock (_lock)
        {
            var oldConnectionString = _currentConnectionString;
            _currentCredentials = credentials;
            _currentConnectionString = BuildConnectionString(credentials);

            _logger.LogInformation(
                "Connection string updated for user: {Username}",
                credentials.Username);

            // Clear the old connection pool to force new connections with new credentials
            if (!string.IsNullOrEmpty(oldConnectionString) && oldConnectionString != _currentConnectionString)
            {
                try
                {
                    _connectionProvider.ClearPool(oldConnectionString);
                    _logger.LogDebug("Cleared old connection pool after credential rotation");
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex, "Failed to clear old connection pool");
                }
            }
        }
    }

    /// <inheritdoc />
    public DbConnection CreateConnection()
    {
        var connectionString = _currentConnectionString;

        if (string.IsNullOrEmpty(connectionString))
        {
            throw new InvalidOperationException(
                "No credentials available. Ensure the CredentialsProviderService has started and credentials exist.");
        }

        return _connectionProvider.CreateConnection(connectionString);
    }

    /// <inheritdoc />
    public DbConnection CreateOpenConnection()
    {
        var connection = CreateConnection();
        try
        {
            connection.Open();
            return connection;
        }
        catch
        {
            connection.Dispose();
            throw;
        }
    }

    /// <inheritdoc />
    public async Task<DbConnection> CreateOpenConnectionAsync(CancellationToken cancellationToken = default)
    {
        var connection = CreateConnection();
        try
        {
            await connection.OpenAsync(cancellationToken);
            return connection;
        }
        catch
        {
            await connection.DisposeAsync();
            throw;
        }
    }

    /// <inheritdoc />
    public string GetConnectionString()
    {
        var connectionString = _currentConnectionString;

        if (string.IsNullOrEmpty(connectionString))
        {
            throw new InvalidOperationException(
                "No credentials available. Ensure the CredentialsProviderService has started and credentials exist.");
        }

        return connectionString;
    }

    /// <summary>
    /// Gets the current credentials (for testing/debugging purposes).
    /// </summary>
    public DatabaseCredentials? CurrentCredentials => _currentCredentials;

    private string BuildConnectionString(DatabaseCredentials credentials)
    {
        if (string.IsNullOrEmpty(_options.ConnectionStringTemplate))
        {
            throw new InvalidOperationException(
                "ConnectionStringTemplate is not configured. Set it in RotatingSecretsOptions.");
        }

        return _options.ConnectionStringTemplate
            .Replace("{Username}", credentials.Username)
            .Replace("{Password}", credentials.Password);
    }
}
