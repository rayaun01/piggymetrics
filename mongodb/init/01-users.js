const password = process.env.MONGODB_PASSWORD || "password";
const databases = [
    "piggymetrics_auth",
    "piggymetrics_accounts",
    "piggymetrics_statistics",
    "piggymetrics_notifications"
];

databases.forEach(function (databaseName) {
    const database = db.getSiblingDB(databaseName);
    if (database.getUser("user") === null) {
        database.createUser({
            user: "user",
            pwd: password,
            roles: [{ role: "readWrite", db: databaseName }]
        });
    }
});
