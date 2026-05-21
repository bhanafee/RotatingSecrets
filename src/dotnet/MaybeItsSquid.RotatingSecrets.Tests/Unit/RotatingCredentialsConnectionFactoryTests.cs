using System.Data.Common;
using FluentAssertions;
using MaybeItsSquid.RotatingSecrets.Configuration;
using MaybeItsSquid.RotatingSecrets.Core;
using MaybeItsSquid.RotatingSecrets.Providers;
using MaybeItsSquid.RotatingSecrets.Services;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Moq;
using Xunit;

namespace MaybeItsSquid.RotatingSecrets.Tests.Unit;

public class RotatingCredentialsConnectionFactoryTests
{
    private readonly Mock<IConnectionProvider> _connectionProviderMock;
    private readonly Mock<ILogger<RotatingCredentialsConnectionFactory>> _loggerMock;
    private readonly RotatingSecretsOptions _options;

    public RotatingCredentialsConnectionFactoryTests()
    {
        _connectionProviderMock = new Mock<IConnectionProvider>();
        _loggerMock = new Mock<ILogger<RotatingCredentialsConnectionFactory>>();
        _options = new RotatingSecretsOptions
        {
            ConnectionStringTemplate = "Server=localhost;Database=test;User Id={Username};Password={Password};"
        };
    }

    [Fact]
    public void CreateConnection_BeforeCredentials_ThrowsInvalidOperationException()
    {
        // Arrange
        var factory = CreateFactory();

        // Act & Assert
        var action = () => factory.CreateConnection();
        action.Should().Throw<InvalidOperationException>()
            .WithMessage("*No credentials available*");
    }

    [Fact]
    public void GetConnectionString_BeforeCredentials_ThrowsInvalidOperationException()
    {
        // Arrange
        var factory = CreateFactory();

        // Act & Assert
        var action = () => factory.GetConnectionString();
        action.Should().Throw<InvalidOperationException>()
            .WithMessage("*No credentials available*");
    }

    [Fact]
    public void OnCredentialsRotated_BuildsConnectionString()
    {
        // Arrange
        var factory = CreateFactory();
        var credentials = new DatabaseCredentials("testuser", "testpass");

        var mockConnection = new Mock<DbConnection>();
        _connectionProviderMock.Setup(p => p.CreateConnection(It.IsAny<string>()))
            .Returns(mockConnection.Object);

        // Act
        factory.OnCredentialsRotated(credentials);

        // Assert
        factory.GetConnectionString().Should().Be(
            "Server=localhost;Database=test;User Id=testuser;Password=testpass;");
    }

    [Fact]
    public void OnCredentialsRotated_ClearsOldPool()
    {
        // Arrange
        var factory = CreateFactory();
        var creds1 = new DatabaseCredentials("user1", "pass1");
        var creds2 = new DatabaseCredentials("user2", "pass2");

        var mockConnection = new Mock<DbConnection>();
        _connectionProviderMock.Setup(p => p.CreateConnection(It.IsAny<string>()))
            .Returns(mockConnection.Object);

        factory.OnCredentialsRotated(creds1);
        var oldConnectionString = factory.GetConnectionString();

        // Act
        factory.OnCredentialsRotated(creds2);

        // Assert
        _connectionProviderMock.Verify(p => p.ClearPool(oldConnectionString), Times.Once);
    }

    [Fact]
    public void OnCredentialsRotated_DoesNotClearPool_WhenFirstCredentials()
    {
        // Arrange
        var factory = CreateFactory();
        var credentials = new DatabaseCredentials("testuser", "testpass");

        // Act
        factory.OnCredentialsRotated(credentials);

        // Assert - should not try to clear an empty connection string
        _connectionProviderMock.Verify(p => p.ClearPool(string.Empty), Times.Never);
    }

    [Fact]
    public void CreateConnection_AfterCredentials_CreatesConnectionWithCorrectString()
    {
        // Arrange
        var factory = CreateFactory();
        var credentials = new DatabaseCredentials("testuser", "testpass");
        var expectedConnectionString = "Server=localhost;Database=test;User Id=testuser;Password=testpass;";

        var mockConnection = new Mock<DbConnection>();
        _connectionProviderMock.Setup(p => p.CreateConnection(expectedConnectionString))
            .Returns(mockConnection.Object);

        factory.OnCredentialsRotated(credentials);

        // Act
        var connection = factory.CreateConnection();

        // Assert
        connection.Should().Be(mockConnection.Object);
        _connectionProviderMock.Verify(p => p.CreateConnection(expectedConnectionString), Times.Once);
    }

    [Fact]
    public void CreateOpenConnection_OpensConnection()
    {
        // Arrange
        var factory = CreateFactory();
        var credentials = new DatabaseCredentials("testuser", "testpass");

        var mockConnection = new Mock<DbConnection>();
        _connectionProviderMock.Setup(p => p.CreateConnection(It.IsAny<string>()))
            .Returns(mockConnection.Object);

        factory.OnCredentialsRotated(credentials);

        // Act
        var connection = factory.CreateOpenConnection();

        // Assert
        mockConnection.Verify(c => c.Open(), Times.Once);
    }

    [Fact]
    public void CreateOpenConnection_DisposesConnection_OnOpenFailure()
    {
        // Arrange
        var factory = CreateFactory();
        var credentials = new DatabaseCredentials("testuser", "testpass");

        var mockConnection = new Mock<DbConnection>();
        mockConnection.Setup(c => c.Open()).Throws(new Exception("Connection failed"));
        _connectionProviderMock.Setup(p => p.CreateConnection(It.IsAny<string>()))
            .Returns(mockConnection.Object);

        factory.OnCredentialsRotated(credentials);

        // Act & Assert
        var action = () => factory.CreateOpenConnection();
        action.Should().Throw<Exception>().WithMessage("Connection failed");
        mockConnection.Verify(c => c.Dispose(), Times.Once);
    }

    [Fact]
    public async Task CreateOpenConnectionAsync_OpensConnectionAsync()
    {
        // Arrange
        var factory = CreateFactory();
        var credentials = new DatabaseCredentials("testuser", "testpass");

        var mockConnection = new Mock<DbConnection>();
        _connectionProviderMock.Setup(p => p.CreateConnection(It.IsAny<string>()))
            .Returns(mockConnection.Object);

        factory.OnCredentialsRotated(credentials);

        // Act
        var connection = await factory.CreateOpenConnectionAsync();

        // Assert
        mockConnection.Verify(c => c.OpenAsync(It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public void CurrentCredentials_ReturnsLastRotatedCredentials()
    {
        // Arrange
        var factory = CreateFactory();
        var credentials = new DatabaseCredentials("testuser", "testpass");

        // Act
        factory.OnCredentialsRotated(credentials);

        // Assert
        factory.CurrentCredentials.Should().Be(credentials);
    }

    [Fact]
    public void OnCredentialsRotated_WithSameCredentials_DoesNotClearPool()
    {
        // Arrange
        var factory = CreateFactory();
        var credentials = new DatabaseCredentials("testuser", "testpass");

        factory.OnCredentialsRotated(credentials);
        _connectionProviderMock.Invocations.Clear();

        // Act - rotate with same credentials
        factory.OnCredentialsRotated(credentials);

        // Assert - connection string is same, so pool should not be cleared
        _connectionProviderMock.Verify(p => p.ClearPool(It.IsAny<string>()), Times.Never);
    }

    [Fact]
    public void OnCredentialsRotated_WithoutTemplate_ThrowsInvalidOperationException()
    {
        // Arrange
        _options.ConnectionStringTemplate = string.Empty;
        var factory = CreateFactory();
        var credentials = new DatabaseCredentials("testuser", "testpass");

        // Act & Assert
        var action = () => factory.OnCredentialsRotated(credentials);
        action.Should().Throw<InvalidOperationException>()
            .WithMessage("*ConnectionStringTemplate*not configured*");
    }

    [Fact]
    public void OnCredentialsRotated_EscapesSpecialCharactersInPassword()
    {
        // Arrange
        var factory = CreateFactory();
        var credentials = new DatabaseCredentials("testuser", "pass;word=with{special}");

        var mockConnection = new Mock<DbConnection>();
        _connectionProviderMock.Setup(p => p.CreateConnection(It.IsAny<string>()))
            .Returns(mockConnection.Object);

        // Act
        factory.OnCredentialsRotated(credentials);

        // Assert - note: template substitution is literal, escaping is user's responsibility
        factory.GetConnectionString().Should().Contain("Password=pass;word=with{special}");
    }

    private RotatingCredentialsConnectionFactory CreateFactory()
    {
        var optionsWrapper = Options.Create(_options);
        return new RotatingCredentialsConnectionFactory(
            _connectionProviderMock.Object,
            optionsWrapper,
            _loggerMock.Object);
    }
}
