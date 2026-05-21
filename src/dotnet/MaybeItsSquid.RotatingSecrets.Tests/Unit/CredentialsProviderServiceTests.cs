using FluentAssertions;
using MaybeItsSquid.RotatingSecrets.Configuration;
using MaybeItsSquid.RotatingSecrets.Core;
using MaybeItsSquid.RotatingSecrets.Services;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Moq;
using Xunit;

namespace MaybeItsSquid.RotatingSecrets.Tests.Unit;

public class CredentialsProviderServiceTests
{
    private readonly Mock<ISecretFileReader> _secretFileReaderMock;
    private readonly Mock<ILogger<CredentialsProviderService>> _loggerMock;
    private readonly RotatingSecretsOptions _options;

    public CredentialsProviderServiceTests()
    {
        _secretFileReaderMock = new Mock<ISecretFileReader>();
        _loggerMock = new Mock<ILogger<CredentialsProviderService>>();
        _options = new RotatingSecretsOptions
        {
            RefreshIntervalMs = 100 // Short interval for testing
        };
    }

    [Fact]
    public async Task RefreshCredentialsAsync_WhenCredentialsChange_NotifiesSubscribers()
    {
        // Arrange
        var credentials = new DatabaseCredentials("user1", "pass1");
        _secretFileReaderMock.Setup(r => r.ReadCredentialsAsync(It.IsAny<CancellationToken>()))
            .ReturnsAsync(credentials);

        var service = CreateService();
        var subscriber = new Mock<ICredentialUpdatable>();
        service.Subscribe(subscriber.Object);

        // Act
        await service.RefreshCredentialsAsync(CancellationToken.None);

        // Assert
        subscriber.Verify(s => s.OnCredentialsRotated(credentials), Times.Once);
    }

    [Fact]
    public async Task RefreshCredentialsAsync_WhenCredentialsUnchanged_DoesNotNotifySubscribers()
    {
        // Arrange
        var credentials = new DatabaseCredentials("user1", "pass1");
        _secretFileReaderMock.Setup(r => r.ReadCredentialsAsync(It.IsAny<CancellationToken>()))
            .ReturnsAsync(credentials);

        var service = CreateService();
        var subscriber = new Mock<ICredentialUpdatable>();
        service.Subscribe(subscriber.Object);

        // Initial load
        await service.RefreshCredentialsAsync(CancellationToken.None);
        subscriber.Invocations.Clear();

        // Act - refresh with same credentials
        await service.RefreshCredentialsAsync(CancellationToken.None);

        // Assert - should not notify again
        subscriber.Verify(s => s.OnCredentialsRotated(It.IsAny<DatabaseCredentials>()), Times.Never);
    }

    [Fact]
    public async Task RefreshCredentialsAsync_WhenCredentialsRotate_NotifiesAllSubscribers()
    {
        // Arrange
        var creds1 = new DatabaseCredentials("user1", "pass1");
        var creds2 = new DatabaseCredentials("user2", "pass2");

        _secretFileReaderMock.SetupSequence(r => r.ReadCredentialsAsync(It.IsAny<CancellationToken>()))
            .ReturnsAsync(creds1)
            .ReturnsAsync(creds2);

        var service = CreateService();
        var subscriber1 = new Mock<ICredentialUpdatable>();
        var subscriber2 = new Mock<ICredentialUpdatable>();
        service.Subscribe(subscriber1.Object);
        service.Subscribe(subscriber2.Object);

        // Initial load
        await service.RefreshCredentialsAsync(CancellationToken.None);
        subscriber1.Invocations.Clear();
        subscriber2.Invocations.Clear();

        // Act - rotate credentials
        await service.RefreshCredentialsAsync(CancellationToken.None);

        // Assert
        subscriber1.Verify(s => s.OnCredentialsRotated(creds2), Times.Once);
        subscriber2.Verify(s => s.OnCredentialsRotated(creds2), Times.Once);
    }

    [Fact]
    public async Task RefreshCredentialsAsync_WhenFilesNotAvailable_DoesNotNotify()
    {
        // Arrange
        _secretFileReaderMock.Setup(r => r.ReadCredentialsAsync(It.IsAny<CancellationToken>()))
            .ReturnsAsync((DatabaseCredentials?)null);

        var service = CreateService();
        var subscriber = new Mock<ICredentialUpdatable>();
        service.Subscribe(subscriber.Object);

        // Act
        await service.RefreshCredentialsAsync(CancellationToken.None);

        // Assert
        subscriber.Verify(s => s.OnCredentialsRotated(It.IsAny<DatabaseCredentials>()), Times.Never);
        service.SecretsAvailable.Should().BeFalse();
    }

    [Fact]
    public void Subscribe_NewSubscriber_ReceivesCurrentCredentials()
    {
        // Arrange
        var credentials = new DatabaseCredentials("user1", "pass1");
        _secretFileReaderMock.Setup(r => r.ReadCredentialsAsync(It.IsAny<CancellationToken>()))
            .ReturnsAsync(credentials);

        var service = CreateService();

        // Load credentials first
        service.RefreshCredentialsAsync(CancellationToken.None).Wait();

        // Act - subscribe after credentials are loaded
        var subscriber = new Mock<ICredentialUpdatable>();
        service.Subscribe(subscriber.Object);

        // Assert - should immediately receive current credentials
        subscriber.Verify(s => s.OnCredentialsRotated(credentials), Times.Once);
    }

    [Fact]
    public void Unsubscribe_RemovesSubscriber()
    {
        // Arrange
        var service = CreateService();
        var subscriber = new Mock<ICredentialUpdatable>();
        service.Subscribe(subscriber.Object);
        service.Unsubscribe(subscriber.Object);

        var credentials = new DatabaseCredentials("user1", "pass1");
        _secretFileReaderMock.Setup(r => r.ReadCredentialsAsync(It.IsAny<CancellationToken>()))
            .ReturnsAsync(credentials);

        // Act
        service.RefreshCredentialsAsync(CancellationToken.None).Wait();

        // Assert - should not be notified
        subscriber.Verify(s => s.OnCredentialsRotated(It.IsAny<DatabaseCredentials>()), Times.Never);
    }

    [Fact]
    public async Task RefreshCredentialsAsync_WhenSubscriberThrows_ContinuesNotifyingOthers()
    {
        // Arrange
        var credentials = new DatabaseCredentials("user1", "pass1");
        _secretFileReaderMock.Setup(r => r.ReadCredentialsAsync(It.IsAny<CancellationToken>()))
            .ReturnsAsync(credentials);

        var service = CreateService();

        var throwingSubscriber = new Mock<ICredentialUpdatable>();
        throwingSubscriber.Setup(s => s.OnCredentialsRotated(It.IsAny<DatabaseCredentials>()))
            .Throws(new Exception("Subscriber error"));

        var goodSubscriber = new Mock<ICredentialUpdatable>();

        service.Subscribe(throwingSubscriber.Object);
        service.Subscribe(goodSubscriber.Object);

        // Act
        await service.RefreshCredentialsAsync(CancellationToken.None);

        // Assert - good subscriber should still be notified
        goodSubscriber.Verify(s => s.OnCredentialsRotated(credentials), Times.Once);
    }

    [Fact]
    public void CurrentCredentials_InitiallyNull()
    {
        // Arrange & Act
        var service = CreateService();

        // Assert
        service.CurrentCredentials.Should().BeNull();
    }

    [Fact]
    public async Task CurrentCredentials_AfterRefresh_ReturnsCurrentCredentials()
    {
        // Arrange
        var credentials = new DatabaseCredentials("user1", "pass1");
        _secretFileReaderMock.Setup(r => r.ReadCredentialsAsync(It.IsAny<CancellationToken>()))
            .ReturnsAsync(credentials);

        var service = CreateService();

        // Act
        await service.RefreshCredentialsAsync(CancellationToken.None);

        // Assert
        service.CurrentCredentials.Should().Be(credentials);
    }

    [Fact]
    public async Task RefreshCredentialsAsync_OnlyUsernameChanged_NotifiesSubscribers()
    {
        // Arrange
        var creds1 = new DatabaseCredentials("user1", "pass1");
        var creds2 = new DatabaseCredentials("user2", "pass1"); // Same password

        _secretFileReaderMock.SetupSequence(r => r.ReadCredentialsAsync(It.IsAny<CancellationToken>()))
            .ReturnsAsync(creds1)
            .ReturnsAsync(creds2);

        var service = CreateService();
        var subscriber = new Mock<ICredentialUpdatable>();
        service.Subscribe(subscriber.Object);

        // Initial load
        await service.RefreshCredentialsAsync(CancellationToken.None);
        subscriber.Invocations.Clear();

        // Act
        await service.RefreshCredentialsAsync(CancellationToken.None);

        // Assert
        subscriber.Verify(s => s.OnCredentialsRotated(creds2), Times.Once);
    }

    [Fact]
    public async Task RefreshCredentialsAsync_OnlyPasswordChanged_NotifiesSubscribers()
    {
        // Arrange
        var creds1 = new DatabaseCredentials("user1", "pass1");
        var creds2 = new DatabaseCredentials("user1", "pass2"); // Same username

        _secretFileReaderMock.SetupSequence(r => r.ReadCredentialsAsync(It.IsAny<CancellationToken>()))
            .ReturnsAsync(creds1)
            .ReturnsAsync(creds2);

        var service = CreateService();
        var subscriber = new Mock<ICredentialUpdatable>();
        service.Subscribe(subscriber.Object);

        // Initial load
        await service.RefreshCredentialsAsync(CancellationToken.None);
        subscriber.Invocations.Clear();

        // Act
        await service.RefreshCredentialsAsync(CancellationToken.None);

        // Assert
        subscriber.Verify(s => s.OnCredentialsRotated(creds2), Times.Once);
    }

    private CredentialsProviderService CreateService()
    {
        var optionsWrapper = Options.Create(_options);
        return new CredentialsProviderService(
            _secretFileReaderMock.Object,
            optionsWrapper,
            _loggerMock.Object);
    }
}
