using MaybeItsSquid.RotatingSecrets.Configuration;
using MaybeItsSquid.RotatingSecrets.Core;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace MaybeItsSquid.RotatingSecrets.Services;

/// <summary>
/// Background service that polls for credential changes and notifies subscribers.
/// This is the .NET equivalent of the Java CredentialsProviderService.
/// </summary>
public class CredentialsProviderService : BackgroundService
{
    private readonly ISecretFileReader _secretFileReader;
    private readonly RotatingSecretsOptions _options;
    private readonly ILogger<CredentialsProviderService> _logger;
    private readonly List<ICredentialUpdatable> _subscribers = new();
    private readonly object _lock = new();

    private volatile DatabaseCredentials? _currentCredentials;
    private volatile bool _secretsAvailable;

    /// <summary>
    /// Initializes a new instance of the <see cref="CredentialsProviderService"/> class.
    /// </summary>
    /// <param name="secretFileReader">The secret file reader.</param>
    /// <param name="options">Configuration options.</param>
    /// <param name="logger">Logger instance.</param>
    public CredentialsProviderService(
        ISecretFileReader secretFileReader,
        IOptions<RotatingSecretsOptions> options,
        ILogger<CredentialsProviderService> logger)
    {
        _secretFileReader = secretFileReader;
        _options = options.Value;
        _logger = logger;
    }

    /// <summary>
    /// Gets the current credentials (may be null if not yet loaded).
    /// </summary>
    public DatabaseCredentials? CurrentCredentials => _currentCredentials;

    /// <summary>
    /// Gets whether secrets are available.
    /// </summary>
    public bool SecretsAvailable => _secretsAvailable;

    /// <summary>
    /// Registers a subscriber to receive credential rotation notifications.
    /// </summary>
    /// <param name="subscriber">The subscriber to register.</param>
    public void Subscribe(ICredentialUpdatable subscriber)
    {
        lock (_lock)
        {
            if (!_subscribers.Contains(subscriber))
            {
                _subscribers.Add(subscriber);
                _logger.LogDebug("Registered credential subscriber: {Type}", subscriber.GetType().Name);

                // If we already have credentials, notify the new subscriber immediately
                if (_currentCredentials != null)
                {
                    subscriber.OnCredentialsRotated(_currentCredentials);
                }
            }
        }
    }

    /// <summary>
    /// Unregisters a subscriber from credential rotation notifications.
    /// </summary>
    /// <param name="subscriber">The subscriber to unregister.</param>
    public void Unsubscribe(ICredentialUpdatable subscriber)
    {
        lock (_lock)
        {
            _subscribers.Remove(subscriber);
            _logger.LogDebug("Unregistered credential subscriber: {Type}", subscriber.GetType().Name);
        }
    }

    /// <inheritdoc />
    public override async Task StartAsync(CancellationToken cancellationToken)
    {
        _logger.LogInformation(
            "Starting credentials provider service. Polling interval: {Interval}ms, Secrets path: {Path}",
            _options.RefreshIntervalMs,
            _options.SecretsPath);

        // Validate permissions on startup
        _secretFileReader.ValidatePermissions();

        // Perform initial credential load
        await RefreshCredentialsAsync(cancellationToken);

        await base.StartAsync(cancellationToken);
    }

    /// <inheritdoc />
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(_options.RefreshIntervalMs));

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                if (await timer.WaitForNextTickAsync(stoppingToken))
                {
                    await RefreshCredentialsAsync(stoppingToken);
                }
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                // Normal shutdown
                break;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error during credential refresh");
                // Continue running - don't let a transient error stop the service
            }
        }

        _logger.LogInformation("Credentials provider service stopped");
    }

    /// <summary>
    /// Refreshes credentials from files and notifies subscribers if changed.
    /// </summary>
    internal async Task RefreshCredentialsAsync(CancellationToken cancellationToken)
    {
        var newCredentials = await _secretFileReader.ReadCredentialsAsync(cancellationToken);

        if (newCredentials == null)
        {
            if (_secretsAvailable)
            {
                _logger.LogWarning("Secret files no longer available at {Path}", _options.SecretsPath);
                _secretsAvailable = false;
            }
            return;
        }

        if (!_secretsAvailable)
        {
            _logger.LogInformation("Secret files now available at {Path}", _options.SecretsPath);
            _secretsAvailable = true;
        }

        // Compare and swap with synchronization
        lock (_lock)
        {
            var hasChanged = _currentCredentials == null ||
                             !_currentCredentials.Equals(newCredentials);

            if (hasChanged)
            {
                _logger.LogInformation(
                    "Credentials changed (username: {OldUsername} -> {NewUsername})",
                    _currentCredentials?.Username ?? "(none)",
                    newCredentials.Username);

                _currentCredentials = newCredentials;
                NotifySubscribers(newCredentials);
            }
        }
    }

    private void NotifySubscribers(DatabaseCredentials credentials)
    {
        // Take a snapshot of subscribers to iterate
        List<ICredentialUpdatable> subscribersSnapshot;
        lock (_lock)
        {
            subscribersSnapshot = new List<ICredentialUpdatable>(_subscribers);
        }

        foreach (var subscriber in subscribersSnapshot)
        {
            try
            {
                subscriber.OnCredentialsRotated(credentials);
                _logger.LogDebug("Notified subscriber: {Type}", subscriber.GetType().Name);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex,
                    "Failed to notify subscriber {Type} of credential rotation",
                    subscriber.GetType().Name);
            }
        }

        _logger.LogInformation("Notified {Count} subscribers of credential rotation", subscribersSnapshot.Count);
    }
}
