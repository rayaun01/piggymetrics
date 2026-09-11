using System;
using System.Collections.Generic;
using Microsoft.Extensions.Configuration;
using Xunit;

namespace PiggyMetrics.Compliance.Tests
{
    public class ConfigurationTests
    {
        [Fact]
        public void MongoSettingsFailFastWhenConnectionStringMissing()
        {
            Assert.Throws<InvalidOperationException>(() => MongoSettings.FromConfiguration(Config()));
        }

        [Fact]
        public void MongoSettingsReadConnectionStringAndDatabase()
        {
            var settings = MongoSettings.FromConfiguration(Config(
                ("Mongo:ConnectionString", "mongodb://u:p@host:27017/compliance"),
                ("Mongo:Database", "compliance_test")));

            Assert.Equal("mongodb://u:p@host:27017/compliance", settings.ConnectionString);
            Assert.Equal("compliance_test", settings.Database);
        }

        [Fact]
        public void MongoSettingsAcceptLegacyEnvironmentKey()
        {
            var settings = MongoSettings.FromConfiguration(Config(
                ("MONGODB_URI", "mongodb://u:p@host:27017/compliance")));

            Assert.Equal("compliance", settings.Database);
        }

        [Fact]
        public void JwtSettingsFailFastWhenAuthorityMissing()
        {
            Assert.Throws<InvalidOperationException>(() => JwtSettings.FromConfiguration(Config()));
        }

        [Fact]
        public void JwtSettingsDefaultToRequiringHttpsMetadata()
        {
            var settings = JwtSettings.FromConfiguration(Config(
                ("Jwt:Authority", "http://auth-service:5000")));

            Assert.True(settings.RequireHttpsMetadata);
            Assert.Null(settings.Audience);
        }

        private static IConfiguration Config(params (string Key, string Value)[] values)
        {
            var dictionary = new Dictionary<string, string>();
            foreach (var value in values)
            {
                dictionary[value.Key] = value.Value;
            }

            return new ConfigurationBuilder().AddInMemoryCollection(dictionary).Build();
        }
    }
}
