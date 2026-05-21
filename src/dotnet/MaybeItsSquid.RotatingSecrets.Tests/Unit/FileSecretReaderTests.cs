using FluentAssertions;
using MaybeItsSquid.RotatingSecrets.Configuration;
using MaybeItsSquid.RotatingSecrets.Services;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Moq;
using Xunit;

namespace MaybeItsSquid.RotatingSecrets.Tests.Unit;

public class FileSecretReaderTests : IDisposable
{
    private readonly string _tempPath;
    private readonly Mock<ILogger<FileSecretReader>> _loggerMock;
    private readonly RotatingSecretsOptions _options;

    public FileSecretReaderTests()
    {
        _tempPath = Path.Combine(Path.GetTempPath(), $"rotating-secrets-test-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempPath);

        _loggerMock = new Mock<ILogger<FileSecretReader>>();
        _options = new RotatingSecretsOptions
        {
            SecretsPath = _tempPath,
            UsernameFileName = "username",
            PasswordFileName = "password",
            ValidateFilePermissions = false // Disable for tests
        };
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempPath))
        {
            Directory.Delete(_tempPath, true);
        }
    }

    [Fact]
    public void SecretsExist_WhenBothFilesExist_ReturnsTrue()
    {
        // Arrange
        CreateSecretFiles("testuser", "testpass");
        var reader = CreateReader();

        // Act
        var result = reader.SecretsExist();

        // Assert
        result.Should().BeTrue();
    }

    [Fact]
    public void SecretsExist_WhenUsernameFileMissing_ReturnsFalse()
    {
        // Arrange
        File.WriteAllText(Path.Combine(_tempPath, "password"), "testpass");
        var reader = CreateReader();

        // Act
        var result = reader.SecretsExist();

        // Assert
        result.Should().BeFalse();
    }

    [Fact]
    public void SecretsExist_WhenPasswordFileMissing_ReturnsFalse()
    {
        // Arrange
        File.WriteAllText(Path.Combine(_tempPath, "username"), "testuser");
        var reader = CreateReader();

        // Act
        var result = reader.SecretsExist();

        // Assert
        result.Should().BeFalse();
    }

    [Fact]
    public void SecretsExist_WhenNoFilesExist_ReturnsFalse()
    {
        // Arrange
        var reader = CreateReader();

        // Act
        var result = reader.SecretsExist();

        // Assert
        result.Should().BeFalse();
    }

    [Fact]
    public void ReadCredentials_WhenFilesExist_ReturnsCredentials()
    {
        // Arrange
        CreateSecretFiles("testuser", "testpass");
        var reader = CreateReader();

        // Act
        var result = reader.ReadCredentials();

        // Assert
        result.Should().NotBeNull();
        result!.Username.Should().Be("testuser");
        result.Password.Should().Be("testpass");
    }

    [Fact]
    public void ReadCredentials_WhenFilesMissing_ReturnsNull()
    {
        // Arrange
        var reader = CreateReader();

        // Act
        var result = reader.ReadCredentials();

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public void ReadCredentials_TrimsWhitespace()
    {
        // Arrange
        CreateSecretFiles("  testuser  \n", "  testpass  \r\n");
        var reader = CreateReader();

        // Act
        var result = reader.ReadCredentials();

        // Assert
        result.Should().NotBeNull();
        result!.Username.Should().Be("testuser");
        result.Password.Should().Be("testpass");
    }

    [Fact]
    public async Task ReadCredentialsAsync_WhenFilesExist_ReturnsCredentials()
    {
        // Arrange
        CreateSecretFiles("asyncuser", "asyncpass");
        var reader = CreateReader();

        // Act
        var result = await reader.ReadCredentialsAsync();

        // Assert
        result.Should().NotBeNull();
        result!.Username.Should().Be("asyncuser");
        result.Password.Should().Be("asyncpass");
    }

    [Fact]
    public async Task ReadCredentialsAsync_WhenFilesMissing_ReturnsNull()
    {
        // Arrange
        var reader = CreateReader();

        // Act
        var result = await reader.ReadCredentialsAsync();

        // Assert
        result.Should().BeNull();
    }

    [Fact]
    public async Task ReadCredentialsAsync_WithCancellation_ThrowsOperationCanceledException()
    {
        // Arrange
        CreateSecretFiles("testuser", "testpass");
        var reader = CreateReader();
        var cts = new CancellationTokenSource();
        cts.Cancel();

        // Act & Assert
        await Assert.ThrowsAsync<OperationCanceledException>(
            () => reader.ReadCredentialsAsync(cts.Token));
    }

    [Fact]
    public void ReadCredentials_WithCustomFileNames_ReadsCorrectFiles()
    {
        // Arrange
        _options.UsernameFileName = "user.txt";
        _options.PasswordFileName = "pass.txt";
        File.WriteAllText(Path.Combine(_tempPath, "user.txt"), "customuser");
        File.WriteAllText(Path.Combine(_tempPath, "pass.txt"), "custompass");
        var reader = CreateReader();

        // Act
        var result = reader.ReadCredentials();

        // Assert
        result.Should().NotBeNull();
        result!.Username.Should().Be("customuser");
        result.Password.Should().Be("custompass");
    }

    private FileSecretReader CreateReader()
    {
        var optionsWrapper = Options.Create(_options);
        return new FileSecretReader(optionsWrapper, _loggerMock.Object);
    }

    private void CreateSecretFiles(string username, string password)
    {
        File.WriteAllText(Path.Combine(_tempPath, _options.UsernameFileName), username);
        File.WriteAllText(Path.Combine(_tempPath, _options.PasswordFileName), password);
    }
}
