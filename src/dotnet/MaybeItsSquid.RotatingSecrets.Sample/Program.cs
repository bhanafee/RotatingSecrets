using MaybeItsSquid.RotatingSecrets.Configuration;
using MaybeItsSquid.RotatingSecrets.Core;

var builder = Host.CreateApplicationBuilder(args);

// Add rotating secrets with SQL Server provider
builder.Services.AddRotatingSecrets(builder.Configuration)
    .AddSqlServerProvider();

// Alternatively, use PostgreSQL:
// builder.Services.AddRotatingSecrets(builder.Configuration)
//     .AddPostgreSqlProvider();

// Or MySQL:
// builder.Services.AddRotatingSecrets(builder.Configuration)
//     .AddMySqlProvider();

// Or configure programmatically:
// builder.Services.AddRotatingSecrets(options =>
// {
//     options.SecretsPath = "/var/run/secrets/database";
//     options.RefreshIntervalMs = 30000;
//     options.ConnectionStringTemplate = "Server=myserver;Database=mydb;User Id={Username};Password={Password};";
// }).AddSqlServerProvider();

var host = builder.Build();

// Example: Using the connection factory
var connectionFactory = host.Services.GetRequiredService<IDbConnectionFactory>();

// Run the host (this keeps the background service running)
var cts = new CancellationTokenSource();
Console.CancelKeyPress += (_, e) =>
{
    e.Cancel = true;
    cts.Cancel();
};

Console.WriteLine("Rotating Secrets Sample Application");
Console.WriteLine("===================================");
Console.WriteLine($"Secrets will be read from: {builder.Configuration["RotatingSecrets:SecretsPath"]}");
Console.WriteLine("Press Ctrl+C to exit");
Console.WriteLine();

// Start the host to begin credential monitoring
await host.StartAsync(cts.Token);

// Demonstrate periodic connection creation
while (!cts.Token.IsCancellationRequested)
{
    try
    {
        // This will use the current credentials
        var connectionString = connectionFactory.GetConnectionString();
        Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Current connection string available");

        // In a real application, you would use the connection:
        // await using var conn = await connectionFactory.CreateOpenConnectionAsync();
        // var result = await conn.QueryAsync<MyEntity>("SELECT * FROM MyTable");

        await Task.Delay(5000, cts.Token);
    }
    catch (InvalidOperationException ex) when (ex.Message.Contains("No credentials"))
    {
        Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Waiting for credentials...");
        await Task.Delay(2000, cts.Token);
    }
    catch (OperationCanceledException)
    {
        break;
    }
}

await host.StopAsync();
Console.WriteLine("Application stopped");
