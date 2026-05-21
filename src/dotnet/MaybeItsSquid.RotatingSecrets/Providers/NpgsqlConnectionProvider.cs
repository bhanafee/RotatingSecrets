using System.Data.Common;
using Microsoft.Extensions.Logging;

namespace MaybeItsSquid.RotatingSecrets.Providers;

/// <summary>
/// PostgreSQL connection provider using Npgsql.
/// </summary>
/// <remarks>
/// This provider dynamically loads Npgsql to avoid requiring the package as a hard dependency.
/// Users must include the package in their application.
/// </remarks>
public class NpgsqlConnectionProvider : IConnectionProvider
{
    private readonly ILogger<NpgsqlConnectionProvider> _logger;
    private readonly Type? _npgsqlConnectionType;
    private readonly System.Reflection.MethodInfo? _clearPoolMethod;

    /// <summary>
    /// Initializes a new instance of the <see cref="NpgsqlConnectionProvider"/> class.
    /// </summary>
    /// <param name="logger">Logger instance.</param>
    public NpgsqlConnectionProvider(ILogger<NpgsqlConnectionProvider> logger)
    {
        _logger = logger;

        // Try to load NpgsqlConnection type dynamically
        _npgsqlConnectionType = Type.GetType("Npgsql.NpgsqlConnection, Npgsql");

        if (_npgsqlConnectionType == null)
        {
            _logger.LogWarning("Npgsql not found. Install the package to use PostgreSQL provider");
            return;
        }

        // Get the ClearPool static method
        _clearPoolMethod = _npgsqlConnectionType.GetMethod(
            "ClearPool",
            System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.Static,
            null,
            new[] { _npgsqlConnectionType },
            null);

        if (_clearPoolMethod == null)
        {
            _logger.LogWarning("ClearPool method not found on NpgsqlConnection");
        }
    }

    /// <inheritdoc />
    public DbConnection CreateConnection(string connectionString)
    {
        if (_npgsqlConnectionType == null)
        {
            throw new InvalidOperationException(
                "Npgsql is not available. Install the NuGet package.");
        }

        var connection = (DbConnection)Activator.CreateInstance(_npgsqlConnectionType, connectionString)!;
        return connection;
    }

    /// <inheritdoc />
    public void ClearPool(string connectionString)
    {
        if (_npgsqlConnectionType == null || _clearPoolMethod == null)
        {
            _logger.LogWarning("Cannot clear pool: Npgsql not available");
            return;
        }

        try
        {
            // Create a temporary connection to get access to the pool
            using var connection = CreateConnection(connectionString);
            _clearPoolMethod.Invoke(null, new object[] { connection });
            _logger.LogDebug("Cleared PostgreSQL connection pool");
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to clear PostgreSQL connection pool");
        }
    }
}
