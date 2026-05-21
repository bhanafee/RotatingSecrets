using System.Data.Common;
using Microsoft.Extensions.Logging;

namespace MaybeItsSquid.RotatingSecrets.Providers;

/// <summary>
/// SQL Server connection provider using Microsoft.Data.SqlClient.
/// </summary>
/// <remarks>
/// This provider dynamically loads Microsoft.Data.SqlClient to avoid requiring
/// the package as a hard dependency. Users must include the package in their application.
/// </remarks>
public class SqlServerConnectionProvider : IConnectionProvider
{
    private readonly ILogger<SqlServerConnectionProvider> _logger;
    private readonly Type? _sqlConnectionType;
    private readonly System.Reflection.MethodInfo? _clearPoolMethod;

    /// <summary>
    /// Initializes a new instance of the <see cref="SqlServerConnectionProvider"/> class.
    /// </summary>
    /// <param name="logger">Logger instance.</param>
    public SqlServerConnectionProvider(ILogger<SqlServerConnectionProvider> logger)
    {
        _logger = logger;

        // Try to load SqlConnection type dynamically
        _sqlConnectionType = Type.GetType("Microsoft.Data.SqlClient.SqlConnection, Microsoft.Data.SqlClient");

        if (_sqlConnectionType == null)
        {
            _logger.LogWarning(
                "Microsoft.Data.SqlClient not found. Install the package to use SQL Server provider");
            return;
        }

        // Get the ClearPool static method
        _clearPoolMethod = _sqlConnectionType.GetMethod(
            "ClearPool",
            System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.Static,
            null,
            new[] { _sqlConnectionType },
            null);

        if (_clearPoolMethod == null)
        {
            _logger.LogWarning("ClearPool method not found on SqlConnection");
        }
    }

    /// <inheritdoc />
    public DbConnection CreateConnection(string connectionString)
    {
        if (_sqlConnectionType == null)
        {
            throw new InvalidOperationException(
                "Microsoft.Data.SqlClient is not available. Install the NuGet package.");
        }

        var connection = (DbConnection)Activator.CreateInstance(_sqlConnectionType, connectionString)!;
        return connection;
    }

    /// <inheritdoc />
    public void ClearPool(string connectionString)
    {
        if (_sqlConnectionType == null || _clearPoolMethod == null)
        {
            _logger.LogWarning("Cannot clear pool: Microsoft.Data.SqlClient not available");
            return;
        }

        try
        {
            // Create a temporary connection to get access to the pool
            using var connection = CreateConnection(connectionString);
            _clearPoolMethod.Invoke(null, new object[] { connection });
            _logger.LogDebug("Cleared SQL Server connection pool");
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to clear SQL Server connection pool");
        }
    }
}
