// Loaded by seed-local.sh through the Mongo shell.
const password = seedPassword;
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

db = db.getSiblingDB("piggymetrics_accounts");
load("__ACCOUNT_DUMP__");
