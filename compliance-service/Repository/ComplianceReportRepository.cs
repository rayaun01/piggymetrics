using System.Threading.Tasks;
using MongoDB.Driver;
using PiggyMetrics.Compliance.Models;

namespace PiggyMetrics.Compliance.Repository
{
    public interface IComplianceReportRepository
    {
        Task<ComplianceReport> Create(ComplianceReport report);
    }

    public class ComplianceReportRepository : IComplianceReportRepository
    {
        private readonly IMongoCollection<ComplianceReport> _reports;

        public ComplianceReportRepository(IMongoDatabase database)
        {
            _reports = database.GetCollection<ComplianceReport>("compliance_reports");
        }

        public async Task<ComplianceReport> Create(ComplianceReport report)
        {
            await _reports.InsertOneAsync(report);
            return report;
        }
    }
}
