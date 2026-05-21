namespace MaybeItsSquid.RotatingSecrets.Configuration;

/// <summary>
/// Configuration options for the rotating secrets system.
/// </summary>
public class RotatingSecretsOptions
{
    /// <summary>
    /// Configuration section name in appsettings.json.
    /// </summary>
    public const string SectionName = "RotatingSecrets";

    /// <summary>
    /// Path to the directory containing secret files.
    /// Default: /var/run/secrets/database
    /// </summary>
    public string SecretsPath { get; set; } = "/var/run/secrets/database";

    /// <summary>
    /// Name of the file containing the username.
    /// Default: username
    /// </summary>
    public string UsernameFileName { get; set; } = "username";

    /// <summary>
    /// Name of the file containing the password.
    /// Default: password
    /// </summary>
    public string PasswordFileName { get; set; } = "password";

    /// <summary>
    /// Interval in milliseconds between credential refresh checks.
    /// Default: 30000 (30 seconds)
    /// </summary>
    public int RefreshIntervalMs { get; set; } = 30000;

    /// <summary>
    /// Connection string template with {Username} and {Password} placeholders.
    /// Example: "Server=myserver;Database=mydb;User Id={Username};Password={Password};"
    /// </summary>
    public string ConnectionStringTemplate { get; set; } = string.Empty;

    /// <summary>
    /// The database provider type.
    /// </summary>
    public DatabaseProviderType ProviderType { get; set; } = DatabaseProviderType.SqlServer;

    /// <summary>
    /// Whether to validate file permissions on startup (Linux/macOS only).
    /// Default: true
    /// </summary>
    public bool ValidateFilePermissions { get; set; } = true;

    /// <summary>
    /// Gets the full path to the username file.
    /// </summary>
    public string UsernameFilePath => Path.Combine(SecretsPath, UsernameFileName);

    /// <summary>
    /// Gets the full path to the password file.
    /// </summary>
    public string PasswordFilePath => Path.Combine(SecretsPath, PasswordFileName);
}

/// <summary>
/// Supported database provider types.
/// </summary>
public enum DatabaseProviderType
{
    /// <summary>
    /// Microsoft SQL Server using Microsoft.Data.SqlClient
    /// </summary>
    SqlServer,

    /// <summary>
    /// PostgreSQL using Npgsql
    /// </summary>
    PostgreSql,

    /// <summary>
    /// MySQL using MySqlConnector
    /// </summary>
    MySql
}
