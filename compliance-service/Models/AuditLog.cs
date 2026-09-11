using System;
using System.Collections.Generic;
using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace PiggyMetrics.Compliance.Models
{
    public class AuditLog
    {
        [BsonId]
        [BsonRepresentation(BsonType.ObjectId)]
        public string Id { get; set; }

        [BsonElement("eventType")]
        public AuditEventType EventType { get; set; }

        [BsonElement("accountName")]
        public string AccountName { get; set; }

        [BsonElement("serviceName")]
        public string ServiceName { get; set; }

        [BsonElement("action")]
        public string Action { get; set; }

        [BsonElement("details")]
        public string Details { get; set; }

        [BsonElement("ipAddress")]
        public string IpAddress { get; set; }

        [BsonElement("userAgent")]
        public string UserAgent { get; set; }

        [BsonElement("timestamp")]
        public DateTime Timestamp { get; set; }

        [BsonElement("amount")]
        [BsonIgnoreIfNull]
        public decimal? Amount { get; set; }

        [BsonElement("metadata")]
        public Dictionary<string, string> Metadata { get; set; }

        [BsonElement("complianceFlags")]
        public List<string> ComplianceFlags { get; set; }
    }

    public enum AuditEventType
    {
        AccountCreated,
        AccountModified,
        TransactionProcessed,
        LoginAttempt,
        PasswordChanged,
        SettingsModified,
        DataExported,
        ComplianceCheckRun,
        FraudAlertGenerated,
        KycVerification,
        AmlScreening
    }
}
