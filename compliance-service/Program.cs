using System;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using MongoDB.Driver;
using NLog;
using NLog.Web;
using PiggyMetrics.Compliance.Repository;
using PiggyMetrics.Compliance.Services;

namespace PiggyMetrics.Compliance
{
    public class Program
    {
        public static void Main(string[] args)
        {
            var logger = NLog.LogManager.Setup()
                .LoadConfigurationFromFile("nlog.config")
                .GetCurrentClassLogger();
            try
            {
                logger.Debug("Starting Compliance Service");
                BuildApp(args).Run();
            }
            catch (Exception ex)
            {
                logger.Error(ex, "Application stopped due to exception");
                throw;
            }
            finally
            {
                NLog.LogManager.Shutdown();
            }
        }

        public static WebApplication BuildApp(string[] args)
        {
            var builder = WebApplication.CreateBuilder(args);

            builder.Logging.ClearProviders();
            builder.Host.UseNLog();

            builder.WebHost.UseUrls(
                builder.Configuration["ASPNETCORE_URLS"] ?? "http://0.0.0.0:8086");

            builder.Services.AddControllers();

            var mongo = MongoSettings.FromConfiguration(builder.Configuration);
            builder.Services.AddSingleton<IMongoClient>(_ => new MongoClient(mongo.ConnectionString));
            builder.Services.AddScoped<IMongoDatabase>(sp =>
                sp.GetRequiredService<IMongoClient>().GetDatabase(mongo.Database));

            builder.Services.AddScoped<IAuditLogRepository, AuditLogRepository>();
            builder.Services.AddScoped<IComplianceRuleRepository, ComplianceRuleRepository>();
            builder.Services.AddScoped<IComplianceReportRepository, ComplianceReportRepository>();
            builder.Services.AddScoped<IComplianceService, ComplianceServiceImpl>();

            AddJwtAuthentication(builder);

            builder.Services.AddSwaggerGen(c =>
            {
                c.SwaggerDoc("v1", new OpenApiInfo
                {
                    Title = "Compliance Service",
                    Version = "v1",
                    Description = "Regulatory compliance checking, audit logging, and KYC/AML monitoring"
                });

                var scheme = new OpenApiSecurityScheme
                {
                    Name = "Authorization",
                    Type = SecuritySchemeType.Http,
                    Scheme = "bearer",
                    BearerFormat = "JWT",
                    In = ParameterLocation.Header,
                    Reference = new OpenApiReference
                    {
                        Type = ReferenceType.SecurityScheme,
                        Id = JwtBearerDefaults.AuthenticationScheme
                    }
                };
                c.AddSecurityDefinition(JwtBearerDefaults.AuthenticationScheme, scheme);
                c.AddSecurityRequirement(new OpenApiSecurityRequirement { [scheme] = Array.Empty<string>() });
            });

            var app = builder.Build();

            if (app.Environment.IsDevelopment())
            {
                app.UseDeveloperExceptionPage();
            }

            app.UseSwagger();
            app.UseSwaggerUI(c =>
            {
                c.SwaggerEndpoint("/swagger/v1/swagger.json", "Compliance API V1");
            });

            app.UseAuthentication();
            app.UseAuthorization();
            app.MapControllers();

            return app;
        }

        private static void AddJwtAuthentication(WebApplicationBuilder builder)
        {
            var jwt = JwtSettings.FromConfiguration(builder.Configuration);

            builder.Services
                .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
                .AddJwtBearer(options =>
                {
                    options.Authority = jwt.Authority;
                    options.RequireHttpsMetadata = jwt.RequireHttpsMetadata;
                    options.TokenValidationParameters = new TokenValidationParameters
                    {
                        ValidateIssuer = true,
                        ValidIssuer = jwt.Issuer ?? jwt.Authority,
                        ValidateAudience = jwt.Audience != null,
                        ValidAudience = jwt.Audience,
                        ValidateLifetime = true,
                        ValidateIssuerSigningKey = true
                    };
                });

            builder.Services.AddAuthorization();
        }
    }

    public class MongoSettings
    {
        public string ConnectionString { get; private set; }

        public string Database { get; private set; }

        public static MongoSettings FromConfiguration(IConfiguration configuration)
        {
            var connectionString = configuration["Mongo:ConnectionString"]
                ?? configuration["MONGODB_URI"];

            if (string.IsNullOrWhiteSpace(connectionString))
            {
                throw new InvalidOperationException(
                    "No MongoDB connection string configured. Set Mongo__ConnectionString (or MONGODB_URI), "
                    + "including credentials, in the environment.");
            }

            return new MongoSettings
            {
                ConnectionString = connectionString,
                Database = configuration["Mongo:Database"] ?? "compliance"
            };
        }
    }

    public class JwtSettings
    {
        public string Authority { get; private set; }

        public string Issuer { get; private set; }

        public string Audience { get; private set; }

        public bool RequireHttpsMetadata { get; private set; }

        public static JwtSettings FromConfiguration(IConfiguration configuration)
        {
            var authority = configuration["Jwt:Authority"] ?? configuration["JWT_AUTHORITY"];

            if (string.IsNullOrWhiteSpace(authority))
            {
                throw new InvalidOperationException(
                    "No JWT authority configured. Set Jwt__Authority (or JWT_AUTHORITY) to the PiggyMetrics "
                    + "auth-service issuer so bearer tokens on the compliance endpoints can be validated.");
            }

            return new JwtSettings
            {
                Authority = authority,
                Issuer = configuration["Jwt:Issuer"],
                Audience = configuration["Jwt:Audience"],
                RequireHttpsMetadata = configuration.GetValue("Jwt:RequireHttpsMetadata", true)
            };
        }
    }
}
