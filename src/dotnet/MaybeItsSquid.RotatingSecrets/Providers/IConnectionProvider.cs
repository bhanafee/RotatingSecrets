using System.Data.Common;

namespace MaybeItsSquid.RotatingSecrets.Providers;

/// <summary>
/// Abstraction for database-specific connection operations.
/// Each database provider (SQL Server, PostgreSQL, MySQL) implements this interface.
/// </summary>
public interface IConnectionProvider
{
    /// <summary>
    /// Creates a new connection instance with the specified connection string.
    /// </summary>
    /// <param name="connectionString">The connection string.</param>
    /// <returns>A new <see cref="DbConnection"/> instance.</returns>
    DbConnection CreateConnection(string connectionString);

    /// <summary>
    /// Clears the connection pool associated with the given connection string.
    /// In ADO.NET, pools are keyed by connection string, so clearing the old pool
    /// forces new connections to be created with fresh credentials.
    /// </summary>
    /// <param name="connectionString">The connection string whose pool should be cleared.</param>
    void ClearPool(string connectionString);
}
