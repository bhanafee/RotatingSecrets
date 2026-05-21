using MaybeItsSquid.RotatingSecrets.Core;
using MaybeItsSquid.RotatingSecrets.Providers;
using MaybeItsSquid.RotatingSecrets.Services;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Hosting;

namespace MaybeItsSquid.RotatingSecrets.Configuration;

/// <summary>
/// Extension methods for registering rotating secrets services with dependency injection.
/// </summary>
public static class ServiceCollectionExtensions
{
    /// <summary>
    /// Adds rotating secrets services to the service collection.
    /// </summary>
    /// <param name="services">The service collection.</param>
    /// <param name="configuration">The configuration instance.</param>
    /// <returns>A builder for further configuration.</returns>
    public static RotatingSecretsBuilder AddRotatingSecrets(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        return services.AddRotatingSecrets(configuration.GetSection(RotatingSecretsOptions.SectionName));
    }

    /// <summary>
    /// Adds rotating secrets services to the service collection.
    /// </summary>
    /// <param name="services">The service collection.</param>
    /// <param name="configurationSection">The configuration section containing options.</param>
    /// <returns>A builder for further configuration.</returns>
    public static RotatingSecretsBuilder AddRotatingSecrets(
        this IServiceCollection services,
        IConfigurationSection configurationSection)
    {
        services.Configure<RotatingSecretsOptions>(configurationSection);
        return services.AddRotatingSecretsCore();
    }

    /// <summary>
    /// Adds rotating secrets services to the service collection.
    /// </summary>
    /// <param name="services">The service collection.</param>
    /// <param name="configureOptions">Action to configure options.</param>
    /// <returns>A builder for further configuration.</returns>
    public static RotatingSecretsBuilder AddRotatingSecrets(
        this IServiceCollection services,
        Action<RotatingSecretsOptions> configureOptions)
    {
        services.Configure(configureOptions);
        return services.AddRotatingSecretsCore();
    }

    private static RotatingSecretsBuilder AddRotatingSecretsCore(this IServiceCollection services)
    {
        // Register core services
        services.TryAddSingleton<ISecretFileReader, FileSecretReader>();
        services.TryAddSingleton<CredentialsProviderService>();

        // Register the credentials provider as a hosted service
        services.AddHostedService(sp => sp.GetRequiredService<CredentialsProviderService>());

        // Register connection factory
        services.TryAddSingleton<RotatingCredentialsConnectionFactory>();
        services.TryAddSingleton<IDbConnectionFactory>(sp =>
            sp.GetRequiredService<RotatingCredentialsConnectionFactory>());

        // Register the subscription initializer to wire up subscribers at startup
        services.AddSingleton<IHostedService, CredentialSubscriptionInitializer>();

        return new RotatingSecretsBuilder(services);
    }
}

/// <summary>
/// Hosted service that initializes credential subscriptions at startup.
/// This ensures the connection factory receives credential updates.
/// </summary>
internal class CredentialSubscriptionInitializer : IHostedService
{
    private readonly CredentialsProviderService _credentialsService;
    private readonly RotatingCredentialsConnectionFactory _connectionFactory;
    private readonly IEnumerable<ICredentialUpdatable> _additionalSubscribers;

    public CredentialSubscriptionInitializer(
        CredentialsProviderService credentialsService,
        RotatingCredentialsConnectionFactory connectionFactory,
        IEnumerable<ICredentialUpdatable> additionalSubscribers)
    {
        _credentialsService = credentialsService;
        _connectionFactory = connectionFactory;
        _additionalSubscribers = additionalSubscribers;
    }

    public Task StartAsync(CancellationToken cancellationToken)
    {
        // Subscribe the connection factory
        _credentialsService.Subscribe(_connectionFactory);

        // Subscribe any additional subscribers registered via AddSubscriber
        foreach (var subscriber in _additionalSubscribers)
        {
            // Avoid double-subscribing the connection factory
            if (subscriber != _connectionFactory)
            {
                _credentialsService.Subscribe(subscriber);
            }
        }

        return Task.CompletedTask;
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        return Task.CompletedTask;
    }
}

/// <summary>
/// Builder for configuring rotating secrets services.
/// </summary>
public class RotatingSecretsBuilder
{
    /// <summary>
    /// Gets the service collection.
    /// </summary>
    public IServiceCollection Services { get; }

    internal RotatingSecretsBuilder(IServiceCollection services)
    {
        Services = services;
    }

    /// <summary>
    /// Adds the SQL Server connection provider.
    /// </summary>
    /// <returns>The builder for method chaining.</returns>
    public RotatingSecretsBuilder AddSqlServerProvider()
    {
        Services.TryAddSingleton<IConnectionProvider, SqlServerConnectionProvider>();
        return this;
    }

    /// <summary>
    /// Adds the PostgreSQL connection provider.
    /// </summary>
    /// <returns>The builder for method chaining.</returns>
    public RotatingSecretsBuilder AddPostgreSqlProvider()
    {
        Services.TryAddSingleton<IConnectionProvider, NpgsqlConnectionProvider>();
        return this;
    }

    /// <summary>
    /// Adds the MySQL connection provider.
    /// </summary>
    /// <returns>The builder for method chaining.</returns>
    public RotatingSecretsBuilder AddMySqlProvider()
    {
        Services.TryAddSingleton<IConnectionProvider, MySqlConnectionProvider>();
        return this;
    }

    /// <summary>
    /// Adds a custom connection provider.
    /// </summary>
    /// <typeparam name="TProvider">The provider type.</typeparam>
    /// <returns>The builder for method chaining.</returns>
    public RotatingSecretsBuilder AddProvider<TProvider>() where TProvider : class, IConnectionProvider
    {
        Services.TryAddSingleton<IConnectionProvider, TProvider>();
        return this;
    }

    /// <summary>
    /// Registers an additional credential updatable subscriber.
    /// </summary>
    /// <typeparam name="TSubscriber">The subscriber type.</typeparam>
    /// <returns>The builder for method chaining.</returns>
    public RotatingSecretsBuilder AddSubscriber<TSubscriber>() where TSubscriber : class, ICredentialUpdatable
    {
        Services.AddSingleton<TSubscriber>();
        Services.AddSingleton<ICredentialUpdatable>(sp => sp.GetRequiredService<TSubscriber>());
        return this;
    }
}
