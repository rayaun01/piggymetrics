using System;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using PiggyMetrics.Compliance.Models;
using PiggyMetrics.Compliance.Services;

namespace PiggyMetrics.Compliance.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    [Authorize]
    public class ComplianceController : ControllerBase
    {
        private readonly IComplianceService _complianceService;

        public ComplianceController(IComplianceService complianceService)
        {
            _complianceService = complianceService;
        }

        /// <summary>
        /// Log an audit event
        /// </summary>
        [HttpPost("audit")]
        public async Task<ActionResult<AuditLog>> LogAuditEvent([FromBody] AuditLog auditLog)
        {
            if (auditLog == null)
                return BadRequest("Audit log cannot be null");

            var result = await _complianceService.LogEvent(auditLog);
            return Ok(result);
        }

        /// <summary>
        /// Get audit trail for an account
        /// </summary>
        [HttpGet("audit/{accountName}")]
        public async Task<IActionResult> GetAuditTrail(
            string accountName,
            [FromQuery] DateTime? from,
            [FromQuery] DateTime? to)
        {
            var trail = await _complianceService.GetAuditTrail(accountName, from, to);
            return Ok(trail);
        }

        /// <summary>
        /// Run compliance check for an account
        /// </summary>
        [HttpPost("check/{accountName}")]
        public async Task<ActionResult<ComplianceReport>> RunComplianceCheck(
            string accountName,
            [FromQuery] DateTime periodStart,
            [FromQuery] DateTime periodEnd)
        {
            var report = await _complianceService.RunComplianceCheck(accountName, periodStart, periodEnd);
            return Ok(report);
        }

        /// <summary>
        /// Get all active compliance rules
        /// </summary>
        [HttpGet("rules")]
        public async Task<IActionResult> GetActiveRules()
        {
            var rules = await _complianceService.GetActiveRules();
            return Ok(rules);
        }

        /// <summary>
        /// Create a new compliance rule
        /// </summary>
        [HttpPost("rules")]
        public async Task<ActionResult<ComplianceRule>> CreateRule([FromBody] ComplianceRule rule)
        {
            if (rule == null)
                return BadRequest("Rule cannot be null");

            var result = await _complianceService.CreateRule(rule);
            return Ok(result);
        }
    }
}
