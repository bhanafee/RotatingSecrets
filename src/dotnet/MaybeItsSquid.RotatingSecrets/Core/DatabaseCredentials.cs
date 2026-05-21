namespace MaybeItsSquid.RotatingSecrets.Core;

/// <summary>
/// Immutable record representing database credentials.
/// Thread-safe by design - credentials are replaced atomically rather than modified.
/// </summary>
/// <param name="Username">The database username.</param>
/// <param name="Password">The database password.</param>
public sealed record DatabaseCredentials(string Username, string Password)
{
    /// <summary>
    /// Creates empty credentials (for initialization before first read).
    /// </summary>
    public static DatabaseCredentials Empty => new(string.Empty, string.Empty);

    /// <summary>
    /// Returns true if credentials are not empty.
    /// </summary>
    public bool IsValid => !string.IsNullOrEmpty(Username) && !string.IsNullOrEmpty(Password);
}
