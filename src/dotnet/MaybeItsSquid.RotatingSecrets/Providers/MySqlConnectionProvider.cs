using System.Data.Common;
using Microsoft.Extensions.Logging;

namespace MaybeItsSquid.RotatingSecrets.Providers;

/// <summary>
/// MySQL connection provider using MySqlConnector.
/// </summary>
/// <remarks>
/// This provider dynamically loads MySqlConnector to avoid requiring the package as a hard dependency.
/// Users must include the package in their application.
/// </remarks>
public class MySqlConnectionProvider : IConnectionProvider
{
    private readonly ILogger<MySqlConnectionProvider> _logger;
    private readonly Type? _mySqlConnectionType;
    private readonly System.Reflection.MethodInfo? _clearPoolMethod;

    /// <summary>
    /// Initializes a new instance of the <see cref="MySqlConnectionProvider"/> class.
    /// </summary>
    /// <param name="logger">Logger instance.</param>
    public MySqlConnectionProvider(ILogger<MySqlConnectionProvider> logger)
    {
        _logger = logger;

        // Try to load MySqlConnection type dynamically
        _mySqlConnectionType = Type.GetType("MySqlConnector.MySqlConnection, MySqlConnector");

        if (_mySqlConnectionType == null)
        {
            _logger.LogWarning("MySqlConnector not found. Install the package to use MySQL provider");
            return;
        }

        // Get the ClearPool static method
        _clearPoolMethod = _mySqlConnectionType.GetMethod(
            "ClearPool",
            System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.Static,
            null,
            new[] { _mySqlConnectionType },
            null);

        if (_clearPoolMethod == null)
        {
            _logger.LogWarning("ClearPool method not found on MySqlConnection");
        }
    }

    /// <inheritdoc />
    public DbConnection CreateConnection(string connectionString)
    {
        if (_mySqlConnectionType == null)
        {
            throw new InvalidOperationException(
                "MySqlConnector is not available. Install the NuGet package.");
        }

        var connection = (DbConnection)Activator.CreateInstance(_mySqlConnectionType, connectionString)!;
        return connection;
    }

    /// <inheritdoc />
    public void ClearPool(string connectionString)
    {
        if (_mySqlConnectionType == null || _clearPoolMethod == null)
        {
            _logger.LogWarning("Cannot clear pool: MySqlConnector not available");
            return;
        }

        try
        {
            // Create a temporary connection to get access to the pool
            using var connection = CreateConnection(connectionString);
            _clearPoolMethod.Invoke(null, new object[] { connection });
            _logger.LogDebug("Cleared MySQL connection pool");
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to clear MySQL connection pool");
        }
    }
}
