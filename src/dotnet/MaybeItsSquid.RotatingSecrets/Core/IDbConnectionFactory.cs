using System.Data.Common;

namespace MaybeItsSquid.RotatingSecrets.Core;

/// <summary>
/// Factory interface for creating database connections with rotating credentials.
/// </summary>
public interface IDbConnectionFactory
{
    /// <summary>
    /// Creates a new database connection (not opened).
    /// </summary>
    /// <returns>A new <see cref="DbConnection"/> instance.</returns>
    DbConnection CreateConnection();

    /// <summary>
    /// Creates and opens a new database connection.
    /// </summary>
    /// <returns>An opened <see cref="DbConnection"/> instance.</returns>
    DbConnection CreateOpenConnection();

    /// <summary>
    /// Creates and opens a new database connection asynchronously.
    /// </summary>
    /// <param name="cancellationToken">Cancellation token.</param>
    /// <returns>An opened <see cref="DbConnection"/> instance.</returns>
    Task<DbConnection> CreateOpenConnectionAsync(CancellationToken cancellationToken = default);

    /// <summary>
    /// Gets the current connection string (with credentials).
    /// Useful for EF Core integration where a connection string is needed.
    /// </summary>
    /// <returns>The current connection string.</returns>
    string GetConnectionString();
}
