using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging.Abstractions;
using PiggyMetrics.Compliance.Models;
using PiggyMetrics.Compliance.Repository;
using PiggyMetrics.Compliance.Services;
using Xunit;

namespace PiggyMetrics.Compliance.Tests
{
    public class ComplianceServiceImplTests
    {
        private const string Account = "demo";

        private static readonly DateTime PeriodStart = new DateTime(2026, 1, 1, 0, 0, 0, DateTimeKind.Utc);
        private static readonly DateTime PeriodEnd = new DateTime(2026, 2, 1, 0, 0, 0, DateTimeKind.Utc);

        [Fact]
        public async Task NoLogsAndNoRulesIsCompliant()
        {
            var report = await RunCheck(new List<AuditLog>(), new List<ComplianceRule>());

            Assert.Equal(ComplianceStatus.Compliant, report.OverallStatus);
            Assert.Empty(report.Violations);
            Assert.Equal(0, report.RiskScore);
            Assert.Equal(0, report.TotalTransactionsAudited);
        }

        [Fact]
        public async Task TransactionAtAmlThresholdIsReported()
        {
            var logs = new List<AuditLog> { Transaction("tx-1", 10000m) };

            var report = await RunCheck(logs, new List<ComplianceRule>());

            var violation = Assert.Single(report.Violations);
            Assert.Equal("AML-001", violation.RuleCode);
            Assert.Equal("tx-1", violation.TransactionId);
            Assert.Equal(ComplianceStatus.MinorViolations, report.OverallStatus);
            Assert.Equal(1, report.TotalTransactionsAudited);
        }

        [Fact]
        public async Task TransactionBelowAmlThresholdIsNotReported()
        {
            var logs = new List<AuditLog> { Transaction("tx-1", 9999.99m) };

            var report = await RunCheck(logs, new List<ComplianceRule>());

            Assert.Empty(report.Violations);
            Assert.Equal(ComplianceStatus.Compliant, report.OverallStatus);
        }

        [Fact]
        public async Task StronglyTypedAmountTakesPrecedenceOverMetadata()
        {
            var log = Transaction("tx-1", 1m);
            log.Amount = 25000m;

            var report = await RunCheck(new List<AuditLog> { log }, new List<ComplianceRule>());

            Assert.Equal("AML-001", Assert.Single(report.Violations).RuleCode);
        }

        [Theory]
        [InlineData(null)]
        [InlineData("not-a-number")]
        public async Task UnusableAmountIsSurfacedInsteadOfSilentlySkipped(string rawAmount)
        {
            var log = new AuditLog
            {
                Id = "tx-1",
                AccountName = Account,
                EventType = AuditEventType.TransactionProcessed,
                Metadata = rawAmount == null
                    ? new Dictionary<string, string>()
                    : new Dictionary<string, string> { ["amount"] = rawAmount }
            };

            var report = await RunCheck(new List<AuditLog> { log }, new List<ComplianceRule>());

            var violation = Assert.Single(report.Violations);
            Assert.Equal("AML-002", violation.RuleCode);
            Assert.Equal(ComplianceSeverity.Warning, violation.Severity);
        }

        [Fact]
        public async Task AmountIsParsedWithInvariantCulture()
        {
            var report = await RunCheck(
                new List<AuditLog> { Transaction("tx-1", 12000.50m) },
                new List<ComplianceRule>());

            Assert.Equal("AML-001", Assert.Single(report.Violations).RuleCode);
        }

        [Fact]
        public async Task GdprExportWithoutConsentIsAViolation()
        {
            var logs = new List<AuditLog>
            {
                new AuditLog
                {
                    Id = "ex-1",
                    AccountName = Account,
                    EventType = AuditEventType.DataExported,
                    ComplianceFlags = new List<string>()
                }
            };
            var rules = new List<ComplianceRule> { Rule("GDPR-001", ComplianceCategory.GDPR, ComplianceSeverity.Violation) };

            var report = await RunCheck(logs, rules);

            Assert.Equal("GDPR-001", Assert.Single(report.Violations).RuleCode);
            Assert.Equal(ComplianceStatus.MajorViolations, report.OverallStatus);
        }

        [Fact]
        public async Task GdprExportWithVerifiedConsentIsClean()
        {
            var logs = new List<AuditLog>
            {
                new AuditLog
                {
                    Id = "ex-1",
                    AccountName = Account,
                    EventType = AuditEventType.DataExported,
                    ComplianceFlags = new List<string> { "consent_verified" }
                }
            };
            var rules = new List<ComplianceRule> { Rule("GDPR-001", ComplianceCategory.GDPR, ComplianceSeverity.Violation) };

            var report = await RunCheck(logs, rules);

            Assert.Empty(report.Violations);
        }

        [Fact]
        public async Task Psd2LoginWithoutScaIsACriticalViolation()
        {
            var logs = new List<AuditLog>
            {
                new AuditLog
                {
                    Id = "login-1",
                    AccountName = Account,
                    EventType = AuditEventType.LoginAttempt,
                    ComplianceFlags = null
                }
            };
            var rules = new List<ComplianceRule> { Rule("PSD2-001", ComplianceCategory.PSD2, ComplianceSeverity.Critical) };

            var report = await RunCheck(logs, rules);

            Assert.Equal("PSD2-001", Assert.Single(report.Violations).RuleCode);
            Assert.Equal(ComplianceStatus.NonCompliant, report.OverallStatus);
            Assert.Equal(0.4, report.RiskScore, 3);
        }

        [Fact]
        public async Task RiskScoreAccumulatesAndSaturatesAtOne()
        {
            var logs = Enumerable.Range(0, 11)
                .Select(i => Transaction("tx-" + i, 50000m))
                .ToList();

            var report = await RunCheck(logs, new List<ComplianceRule>());

            Assert.Equal(11, report.Violations.Count);
            Assert.Equal(1.0, report.RiskScore, 3);
        }

        [Fact]
        public async Task ReportIsPersisted()
        {
            var reportRepo = new FakeReportRepository();
            var service = new ComplianceServiceImpl(
                new FakeAuditLogRepository(new List<AuditLog>()),
                new FakeRuleRepository(new List<ComplianceRule>()),
                reportRepo,
                NullLogger<ComplianceServiceImpl>.Instance);

            var report = await service.RunComplianceCheck(Account, PeriodStart, PeriodEnd);

            Assert.Same(report, Assert.Single(reportRepo.Saved));
            Assert.Equal(Account, report.AccountName);
            Assert.Equal(PeriodStart, report.PeriodStart);
            Assert.Equal(PeriodEnd, report.PeriodEnd);
        }

        private static Task<ComplianceReport> RunCheck(List<AuditLog> logs, List<ComplianceRule> rules)
        {
            var service = new ComplianceServiceImpl(
                new FakeAuditLogRepository(logs),
                new FakeRuleRepository(rules),
                new FakeReportRepository(),
                NullLogger<ComplianceServiceImpl>.Instance);

            return service.RunComplianceCheck(Account, PeriodStart, PeriodEnd);
        }

        private static AuditLog Transaction(string id, decimal amount)
        {
            return new AuditLog
            {
                Id = id,
                AccountName = Account,
                EventType = AuditEventType.TransactionProcessed,
                Metadata = new Dictionary<string, string>
                {
                    ["amount"] = amount.ToString(System.Globalization.CultureInfo.InvariantCulture)
                }
            };
        }

        private static ComplianceRule Rule(string code, ComplianceCategory category, ComplianceSeverity severity)
        {
            return new ComplianceRule
            {
                RuleCode = code,
                Regulation = category.ToString(),
                Category = category,
                Severity = severity,
                Enabled = true
            };
        }

        private class FakeAuditLogRepository : IAuditLogRepository
        {
            private readonly List<AuditLog> _logs;

            public FakeAuditLogRepository(List<AuditLog> logs)
            {
                _logs = logs;
            }

            public Task<AuditLog> Create(AuditLog log)
            {
                _logs.Add(log);
                return Task.FromResult(log);
            }

            public Task<IEnumerable<AuditLog>> GetByAccount(string accountName, DateTime? from, DateTime? to)
            {
                return Task.FromResult(_logs.Where(l => l.AccountName == accountName));
            }

            public Task<IEnumerable<AuditLog>> GetByEventType(AuditEventType eventType, int limit)
            {
                return Task.FromResult(_logs.Where(l => l.EventType == eventType).Take(limit));
            }

            public Task<long> CountByAccount(string accountName)
            {
                return Task.FromResult((long)_logs.Count(l => l.AccountName == accountName));
            }
        }

        private class FakeRuleRepository : IComplianceRuleRepository
        {
            private readonly List<ComplianceRule> _rules;

            public FakeRuleRepository(List<ComplianceRule> rules)
            {
                _rules = rules;
            }

            public Task<IEnumerable<ComplianceRule>> GetActiveRules()
            {
                return Task.FromResult(_rules.Where(r => r.Enabled));
            }

            public Task<IEnumerable<ComplianceRule>> GetRulesByCategory(ComplianceCategory category)
            {
                return Task.FromResult(_rules.Where(r => r.Enabled && r.Category == category));
            }

            public Task<ComplianceRule> GetByRuleCode(string ruleCode)
            {
                return Task.FromResult(_rules.FirstOrDefault(r => r.RuleCode == ruleCode));
            }

            public Task<ComplianceRule> Create(ComplianceRule rule)
            {
                _rules.Add(rule);
                return Task.FromResult(rule);
            }

            public Task Update(string id, ComplianceRule rule)
            {
                return Task.CompletedTask;
            }
        }

        private class FakeReportRepository : IComplianceReportRepository
        {
            public List<ComplianceReport> Saved { get; } = new List<ComplianceReport>();

            public Task<ComplianceReport> Create(ComplianceReport report)
            {
                Saved.Add(report);
                return Task.FromResult(report);
            }
        }
    }
}
