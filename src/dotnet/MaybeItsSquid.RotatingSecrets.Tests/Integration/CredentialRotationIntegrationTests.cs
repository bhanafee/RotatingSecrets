using System.Collections.Concurrent;
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

namespace MaybeItsSquid.RotatingSecrets.Tests.Integration;

public class CredentialRotationIntegrationTests : IDisposable
{
    private readonly string _tempPath;
    private readonly RotatingSecretsOptions _options;
    private readonly Mock<ILogger<FileSecretReader>> _readerLoggerMock;
    private readonly Mock<ILogger<CredentialsProviderService>> _serviceLoggerMock;
    private readonly Mock<ILogger<RotatingCredentialsConnectionFactory>> _factoryLoggerMock;
    private readonly Mock<IConnectionProvider> _connectionProviderMock;

    public CredentialRotationIntegrationTests()
    {
        _tempPath = Path.Combine(Path.GetTempPath(), $"rotating-secrets-integration-{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempPath);

        _options = new RotatingSecretsOptions
        {
            SecretsPath = _tempPath,
            RefreshIntervalMs = 50, // Fast polling for tests
            ConnectionStringTemplate = "Server=localhost;User Id={Username};Password={Password};",
            ValidateFilePermissions = false
        };

        _readerLoggerMock = new Mock<ILogger<FileSecretReader>>();
        _serviceLoggerMock = new Mock<ILogger<CredentialsProviderService>>();
        _factoryLoggerMock = new Mock<ILogger<RotatingCredentialsConnectionFactory>>();
        _connectionProviderMock = new Mock<IConnectionProvider>();

        _connectionProviderMock.Setup(p => p.CreateConnection(It.IsAny<string>()))
            .Returns(() =>
            {
                var mock = new Mock<DbConnection>();
                return mock.Object;
            });
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempPath))
        {
            Directory.Delete(_tempPath, true);
        }
    }

    [Fact]
    public async Task EndToEnd_CredentialRotation_UpdatesConnectionFactory()
    {
        // Arrange
        var optionsWrapper = Options.Create(_options);
        var reader = new FileSecretReader(optionsWrapper, _readerLoggerMock.Object);
        var service = new CredentialsProviderService(reader, optionsWrapper, _serviceLoggerMock.Object);
        var factory = new RotatingCredentialsConnectionFactory(
            _connectionProviderMock.Object, optionsWrapper, _factoryLoggerMock.Object);

        service.Subscribe(factory);

        // Create initial credentials
        WriteCredentials("user1", "pass1");

        // Act - Start service and wait for initial load
        await service.StartAsync(CancellationToken.None);
        await Task.Delay(100); // Wait for initial load

        // Assert - initial credentials
        factory.GetConnectionString().Should().Contain("User Id=user1");
        factory.GetConnectionString().Should().Contain("Password=pass1");

        // Act - Rotate credentials
        WriteCredentials("user2", "pass2");
        await Task.Delay(150); // Wait for refresh

        // Assert - rotated credentials
        factory.GetConnectionString().Should().Contain("User Id=user2");
        factory.GetConnectionString().Should().Contain("Password=pass2");

        // Cleanup
        await service.StopAsync(CancellationToken.None);
    }

    [Fact]
    public async Task EndToEnd_MultipleSubscribers_AllNotified()
    {
        // Arrange
        var optionsWrapper = Options.Create(_options);
        var reader = new FileSecretReader(optionsWrapper, _readerLoggerMock.Object);
        var service = new CredentialsProviderService(reader, optionsWrapper, _serviceLoggerMock.Object);

        var subscriber1 = new TestSubscriber();
        var subscriber2 = new TestSubscriber();
        var subscriber3 = new TestSubscriber();

        service.Subscribe(subscriber1);
        service.Subscribe(subscriber2);
        service.Subscribe(subscriber3);

        WriteCredentials("user1", "pass1");

        // Act
        await service.StartAsync(CancellationToken.None);
        await Task.Delay(100);

        // Assert
        subscriber1.ReceivedCredentials.Should().HaveCount(1);
        subscriber2.ReceivedCredentials.Should().HaveCount(1);
        subscriber3.ReceivedCredentials.Should().HaveCount(1);

        subscriber1.ReceivedCredentials[0].Username.Should().Be("user1");
        subscriber2.ReceivedCredentials[0].Username.Should().Be("user1");
        subscriber3.ReceivedCredentials[0].Username.Should().Be("user1");

        await service.StopAsync(CancellationToken.None);
    }

    [Fact]
    public async Task ConcurrentAccess_DuringRotation_HandledGracefully()
    {
        // Arrange
        var optionsWrapper = Options.Create(_options);
        var reader = new FileSecretReader(optionsWrapper, _readerLoggerMock.Object);
        var service = new CredentialsProviderService(reader, optionsWrapper, _serviceLoggerMock.Object);
        var factory = new RotatingCredentialsConnectionFactory(
            _connectionProviderMock.Object, optionsWrapper, _factoryLoggerMock.Object);

        service.Subscribe(factory);
        WriteCredentials("user1", "pass1");

        await service.StartAsync(CancellationToken.None);
        await Task.Delay(100);

        var connectionStrings = new ConcurrentBag<string>();
        var errors = new ConcurrentBag<Exception>();
        var cts = new CancellationTokenSource(TimeSpan.FromSeconds(2));

        // Act - Concurrent reads during rotation
        var readTask = Task.Run(async () =>
        {
            while (!cts.Token.IsCancellationRequested)
            {
                try
                {
                    var connStr = factory.GetConnectionString();
                    connectionStrings.Add(connStr);
                    await Task.Delay(10);
                }
                catch (Exception ex)
                {
                    errors.Add(ex);
                }
            }
        });

        var rotateTask = Task.Run(async () =>
        {
            for (int i = 2; i <= 5; i++)
            {
                WriteCredentials($"user{i}", $"pass{i}");
                await Task.Delay(100);
            }
        });

        await Task.WhenAll(readTask, rotateTask);

        // Assert
        errors.Should().BeEmpty("no errors should occur during concurrent access");
        connectionStrings.Should().NotBeEmpty("should have read connection strings");

        // Should see a mix of old and new credentials during rotation
        var uniqueStrings = connectionStrings.Distinct().ToList();
        uniqueStrings.Should().HaveCountGreaterOrEqualTo(1, "should have at least one unique connection string");

        await service.StopAsync(CancellationToken.None);
    }

    [Fact]
    public async Task PoolClearing_OnRotation_CalledForOldConnectionString()
    {
        // Arrange
        var clearedPools = new List<string>();
        _connectionProviderMock.Setup(p => p.ClearPool(It.IsAny<string>()))
            .Callback<string>(connStr => clearedPools.Add(connStr));

        var optionsWrapper = Options.Create(_options);
        var reader = new FileSecretReader(optionsWrapper, _readerLoggerMock.Object);
        var service = new CredentialsProviderService(reader, optionsWrapper, _serviceLoggerMock.Object);
        var factory = new RotatingCredentialsConnectionFactory(
            _connectionProviderMock.Object, optionsWrapper, _factoryLoggerMock.Object);

        service.Subscribe(factory);
        WriteCredentials("user1", "pass1");

        // Act
        await service.StartAsync(CancellationToken.None);
        await Task.Delay(100);

        WriteCredentials("user2", "pass2");
        await Task.Delay(150);

        WriteCredentials("user3", "pass3");
        await Task.Delay(150);

        await service.StopAsync(CancellationToken.None);

        // Assert
        clearedPools.Should().HaveCount(2, "pool should be cleared on each rotation");
        clearedPools[0].Should().Contain("User Id=user1");
        clearedPools[1].Should().Contain("User Id=user2");
    }

    [Fact]
    public async Task SecretsBecomingAvailable_AfterStartup_LoadsCredentials()
    {
        // Arrange
        var optionsWrapper = Options.Create(_options);
        var reader = new FileSecretReader(optionsWrapper, _readerLoggerMock.Object);
        var service = new CredentialsProviderService(reader, optionsWrapper, _serviceLoggerMock.Object);
        var factory = new RotatingCredentialsConnectionFactory(
            _connectionProviderMock.Object, optionsWrapper, _factoryLoggerMock.Object);

        service.Subscribe(factory);

        // Don't create credentials yet
        await service.StartAsync(CancellationToken.None);
        await Task.Delay(100);

        service.SecretsAvailable.Should().BeFalse();

        // Act - Create credentials after service started
        WriteCredentials("user1", "pass1");
        await Task.Delay(150);

        // Assert
        service.SecretsAvailable.Should().BeTrue();
        factory.GetConnectionString().Should().Contain("User Id=user1");

        await service.StopAsync(CancellationToken.None);
    }

    [Fact]
    public async Task ServiceShutdown_Graceful()
    {
        // Arrange
        var optionsWrapper = Options.Create(_options);
        var reader = new FileSecretReader(optionsWrapper, _readerLoggerMock.Object);
        var service = new CredentialsProviderService(reader, optionsWrapper, _serviceLoggerMock.Object);

        WriteCredentials("user1", "pass1");
        await service.StartAsync(CancellationToken.None);

        // Act
        var stopTask = service.StopAsync(CancellationToken.None);

        // Assert - Should complete without hanging
        var completed = await Task.WhenAny(stopTask, Task.Delay(5000)) == stopTask;
        completed.Should().BeTrue("service should stop gracefully within timeout");
    }

    private void WriteCredentials(string username, string password)
    {
        File.WriteAllText(Path.Combine(_tempPath, "username"), username);
        File.WriteAllText(Path.Combine(_tempPath, "password"), password);
    }

    private class TestSubscriber : ICredentialUpdatable
    {
        public List<DatabaseCredentials> ReceivedCredentials { get; } = new();

        public void OnCredentialsRotated(DatabaseCredentials credentials)
        {
            ReceivedCredentials.Add(credentials);
        }
    }
}
