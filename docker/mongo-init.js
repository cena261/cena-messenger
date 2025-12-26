db = db.getSiblingDB("chat_app");

db.createUser({
    user: "chat_user",
    pwd: "chat_pass",
    roles: [{ role: "readWrite", db: "chat_app" }]
});

db.createCollection("users");
db.createCollection("messages");
db.createCollection("conversations");
db.createCollection("conversation_members");
db.createCollection("friends");
db.createCollection("friend_requests");
db.createCollection("refresh_tokens");
