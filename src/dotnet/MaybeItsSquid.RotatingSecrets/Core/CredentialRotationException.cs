namespace MaybeItsSquid.RotatingSecrets.Core;

/// <summary>
/// Exception thrown when credential rotation operations fail.
/// </summary>
public class CredentialRotationException : Exception
{
    /// <summary>
    /// Initializes a new instance of the <see cref="CredentialRotationException"/> class.
    /// </summary>
    public CredentialRotationException()
    {
    }

    /// <summary>
    /// Initializes a new instance of the <see cref="CredentialRotationException"/> class
    /// with a specified error message.
    /// </summary>
    /// <param name="message">The message that describes the error.</param>
    public CredentialRotationException(string message)
        : base(message)
    {
    }

    /// <summary>
    /// Initializes a new instance of the <see cref="CredentialRotationException"/> class
    /// with a specified error message and inner exception.
    /// </summary>
    /// <param name="message">The message that describes the error.</param>
    /// <param name="innerException">The exception that caused this exception.</param>
    public CredentialRotationException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
