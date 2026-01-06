# Cena Messenger

A real-time chat application built with Spring Boot backend and Vue.js frontend, featuring WebSocket-based instant messaging, group conversations, and scalable multi-instance architecture.

![ERD Diagram](https://github.com/cena261/cena-messenger/blob/b47929d42fa62dba83db16455bf973be00538fec/frontend/public/diagram.png)

![UI Design](https://github.com/cena261/cena-messenger/blob/b47929d42fa62dba83db16455bf973be00538fec/frontend/public/design.png)

## Technology Stack

### Backend
- **Framework:** Spring Boot 4.0.1 (Java 21)
- **Database:** MongoDB 7.0+ (primary data store)
- **Cache & Pub/Sub:** Redis 7.x (real-time event distribution)
- **Object Storage:** MinIO (media file storage)
- **Authentication:** JWT (HS384) with refresh tokens
- **WebSocket:** STOMP over WebSocket
- **Email:** Spring Mail (password reset flow)

### Frontend
- **Framework:** Vue 3 (Composition API)
- **State Management:** Pinia
- **Routing:** Vue Router
- **HTTP Client:** Axios
- **WebSocket:** @stomp/stompjs
- **UI:** Custom CSS with theme support (light/dark mode)
- **Build Tool:** Vite

---

## System Architecture

### Core Design Principles

The system follows a **hybrid REST + WebSocket architecture** where:
- **REST API:** Handles all write operations and queries
- **WebSocket:** Delivers real-time events to connected clients
- **MongoDB:** Single source of truth for all persistent data
- **Redis Pub/Sub:** Real-time message fanout across backend instances

### Multi-Instance Architecture

The backend is designed for **horizontal scalability**. Multiple backend instances coordinate through:
1. **Shared MongoDB:** All instances read/write to the same database
2. **Redis Pub/Sub:** Events published by one instance are received by all instances
3. **Stateless Design:** No instance-specific state; any instance can serve any request

```
[Client A] ──WebSocket──> [Backend Instance 1] ──┐
                                                  │
[Client B] ──WebSocket──> [Backend Instance 2] ──┼──> [MongoDB]
                                                  │
[Client C] ──WebSocket──> [Backend Instance 3] ──┘
                    │
                    └──────> [Redis Pub/Sub] ────> Fan-out to all instances
```

---

## How the Backend Operates

### Message Flow (End-to-End)

#### 1. **Client Sends Message via REST API**
```
POST /api/messages
{
  "conversationId": "conv123",
  "content": "Hello World"
}
```

**Backend Processing:**
1. **Authentication:** JWT validated, user identity extracted from security context
2. **Authorization:** Check if user is a member of the conversation
3. **Persistence:** Message saved to MongoDB with server-generated timestamp
4. **Redis Publication:** Message event published to `conversation:conv123:messages` channel
5. **Response:** HTTP 200 returned immediately after Redis publish (async)

#### 2. **Redis Pub/Sub Fanout**
- All backend instances subscribe to `conversation:*:messages` pattern
- Each instance receives the published message event
- Instances filter events to match their connected clients

#### 3. **WebSocket Delivery**
- Each backend instance iterates through its active WebSocket sessions
- For clients subscribed to `/topic/conversation.conv123`, the message is sent via STOMP
- Clients receive message in real-time without polling

### WebSocket Connection Lifecycle

#### **Handshake (HTTP → WebSocket Upgrade)**
```
Client → Backend: GET /ws (with JWT in query param ?token=...)
Backend: Validate JWT signature and expiration
Backend: Extract userId from JWT subject claim
Backend: Upgrade connection to WebSocket
Backend: Store session mapping in Redis
```

#### **Subscription Authorization**
```
Client → Backend: SUBSCRIBE /topic/conversation.conv123
Backend: Check if authenticated user is member of conv123
Backend: If authorized, allow subscription
Backend: If unauthorized, send STOMP ERROR frame and reject
```

#### **Disconnect Cleanup**
```
Client disconnects
Backend: Remove session from Redis presence tracking
Backend: Remove all subscriptions for that session
```

### Authentication & Authorization Flow

#### **Registration**
1. Client sends username, password, displayName
2. Backend hashes password with BCrypt
3. User document saved to MongoDB
4. Access token (JWT, 1 hour expiry) and refresh token (random, 30 days expiry) generated
5. Refresh token persisted to MongoDB
6. Both tokens returned to client

#### **Login**
1. Client sends username + password
2. Backend loads user from MongoDB
3. BCrypt verifies password
4. New access token + refresh token generated
5. Old refresh tokens remain valid (no revocation)

#### **Token Refresh**
1. Client sends refresh token (via HTTP-only cookie)
2. Backend queries MongoDB for token validity
3. If valid and not revoked/expired, new access token issued
4. Refresh token is NOT rotated

#### **Logout**
1. Client sends logout request
2. Backend marks refresh token as revoked in MongoDB
3. Access token remains valid until natural expiration (cannot be revoked)

### Authorization Model

**Membership-Based Access Control:**
- Authorization is enforced through `ConversationMember` collection
- Each member has:
  - `role`: "OWNER" or "MEMBER"
  - `canSendMessage`: Boolean flag for write permission
- **Read Access:** Membership existence is sufficient
- **Write Access:** Membership + `canSendMessage = true`
- **Owner Privileges:** Role "OWNER" can modify conversation metadata

**Enforcement Layers:**
1. **REST Controllers:** Validate membership before executing service methods
2. **WebSocket Interceptors:** Validate membership before allowing subscriptions
3. Both layers independently check the same repository method

### Real-Time Event Types

The backend publishes events to Redis Pub/Sub for real-time delivery:

| Event Type | Redis Channel | Purpose |
|------------|---------------|---------|
| **New Message** | `conversation:{id}:messages` | Message created |
| **Message Update** | `user:{id}:message-updates` | Message edited/deleted |
| **Unread Count** | `user:{id}:unread` | Unread counter changed |
| **Seen Receipt** | `user:{id}:seen` | User marked conversation as read |
| **Typing Indicator** | `user:{id}:typing` | User started/stopped typing (5s TTL) |
| **Reactions** | `user:{id}:reactions` | Reaction added/removed |
| **Group Events** | `user:{id}:group-events` | Member added/removed/role changed |

### Presence Tracking (Redis)

**Session Lifecycle:**
```
Connect:
  SET session:{sessionId}:user {userId}
  SADD presence:user:{userId} {sessionId}

Disconnect:
  GET session:{sessionId}:user → {userId}
  DEL session:{sessionId}:user
  SREM presence:user:{userId} {sessionId}

Check Online:
  EXISTS presence:user:{userId}
  or SCARD presence:user:{userId} > 0
```

**Typing Indicators (Ephemeral):**
```
Start Typing:
  SETEX typing:{conversationId}:{userId} 5 "true"

Stop Typing:
  DEL typing:{conversationId}:{userId}

Auto-Expire: 5 seconds
```

### Data Flow Invariants

1. **MongoDB is Source of Truth:** All persistent data stored in MongoDB. Redis contains only ephemeral state.
2. **WebSocket is Read-Only:** No data persistence via WebSocket. All writes via REST.
3. **User Identity from JWT:** Never trust client-supplied user IDs. Always extract from security context.
4. **Membership Checked Everywhere:** Authorization enforced in both REST and WebSocket layers.
5. **Async Pub/Sub:** REST responses return after MongoDB save + Redis publish (non-blocking).

---

## Deployment Architecture

### Required Infrastructure

```
┌─────────────────────────────────────────────────────┐
│                 Load Balancer                        │
│            (HTTP + WebSocket Support)                │
└───────────┬─────────────────────────┬────────────────┘
            │                         │
┌───────────▼────────┐    ┌───────────▼────────┐
│  Backend Instance  │    │  Backend Instance  │
│     (Port 8080)    │    │     (Port 8080)    │
└─────┬──────────┬───┘    └─────┬──────────┬───┘
      │          │               │          │
      │   ┌──────▼───────────────▼──────┐   │
      │   │       Redis (Pub/Sub)       │   │
      │   │  Port 6379 (No persistence) │   │
      │   └─────────────────────────────┘   │
      │                                      │
   ┌──▼──────────────────────────────────────▼───┐
   │          MongoDB Replica Set                │
   │  Port 27017 (Primary Data Store)            │
   └────────────────────────────────────────────┘
              │
   ┌──────────▼──────────┐
   │       MinIO         │
   │  Object Storage     │
   │  Port 9000          │
   └─────────────────────┘
```

### Environment Variables

**Backend (`application.yml` or environment):**
```yaml
jwt.secret-key: <min 32 chars HMAC secret>
jwt.access-token-validity: 3600000  # 1 hour in ms
jwt.refresh-token-validity: 2592000000  # 30 days in ms

spring.data.mongodb.uri: mongodb://user:pass@host:27017/chatapp
spring.data.redis.host: redis-host
spring.data.redis.port: 6379

minio.url: http://minio:9000
minio.access-key: <access-key>
minio.secret-key: <secret-key>
```

**Frontend (`.env`):**
```
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_URL=ws://localhost:8080/ws
```

### Scaling Considerations

**Horizontal Scaling:**
- Add backend instances behind load balancer
- All instances share MongoDB + Redis
- Sticky sessions recommended but not required
- Each instance maintains separate WebSocket connections

**Connection Limits:**
- Bound by OS file descriptors and JVM thread pool
- Typical: 10,000-50,000 concurrent WebSocket connections per instance
- Scale horizontally for higher concurrency

**Redis Requirements:**
- Pub/Sub only (no persistence needed)
- Redis Cluster supported but not required
- Replication (master-replica) improves availability

**MongoDB Requirements:**
- Replica set recommended for production
- Automatic failover supported
- Indexes auto-created by Spring Data MongoDB

---

## Frontend Overview

The frontend is a **single-page application (SPA)** built with Vue 3, featuring:

### Core Features
- **Real-time Messaging:** WebSocket-based instant message delivery
- **Conversation Management:** Direct messages and group conversations
- **Authentication:** JWT-based with automatic token refresh
- **Theme Support:** Light/dark mode with persistent preference
- **Responsive Design:** Mobile-friendly UI
- **Profile Management:** Avatar upload, display name editing

### State Management (Pinia Stores)
- **auth:** User authentication, login/logout, token management
- **conversations:** Conversation list, active conversation selection
- **messages:** Message history, sending, editing, deletion
- **realtime:** WebSocket connection, typing indicators, presence
- **blocking:** User blocking functionality

### WebSocket Integration
```javascript
// Connection established with JWT
stompClient.connect({ token: accessToken }, () => {
  // Subscribe to conversation topics
  stompClient.subscribe('/topic/conversation.{id}', handleMessage)
  stompClient.subscribe('/user/queue/unread', handleUnread)
  stompClient.subscribe('/user/queue/typing', handleTyping)
})
```

### Performance Optimizations
- **Scroll Throttling:** requestAnimationFrame for smooth scrolling
- **Selective Reactivity:** Removed deep watchers on message arrays
- **Lifecycle Cleanup:** Properly unsubscribe and clear timers on unmount
- **Direct Mutations:** Optimized store updates to prevent cascading re-renders

---

## Testing

### Backend Integration Tests
- **Testcontainers:** Real MongoDB + Redis instances in Docker
- **End-to-End Flow:** REST → MongoDB → Redis → WebSocket delivery
- **Security Tests:** JWT validation, subscription authorization

**Run Tests:**
```bash
cd backend
mvn test
```

### Frontend Manual Testing
- Open multiple browser tabs with different users
- Send messages and verify real-time delivery
- Test conversation switching, typing indicators, seen receipts

---

## Development Setup

### Prerequisites
- **Java 21+**
- **Node.js 18+**
- **Docker + Docker Compose**
- **MongoDB 7.0+**
- **Redis 7.x**

### Quick Start

1. **Start Infrastructure:**
```bash
docker-compose up -d  # MongoDB, Redis, MinIO
```

2. **Backend:**
```bash
cd backend
mvn spring-boot:run
```

3. **Frontend:**
```bash
cd frontend
npm install
npm run dev
```

4. **Access:**
- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080/api`
- WebSocket: `ws://localhost:8080/ws`

---

## Security Model

### Defense in Depth

1. **Stateless JWT Authentication:** No server-side sessions
2. **Membership-Based Authorization:** Every operation checks conversation membership
3. **HTTP-only Refresh Tokens:** Protected from XSS
4. **BCrypt Password Hashing:** Industry-standard (10 rounds)
5. **CORS Configuration:** Restricted frontend origins
6. **WebSocket Authentication:** JWT validated during handshake
7. **Subscription Authorization:** Membership checked before allowing topic subscriptions

### Token Lifecycle
- **Access Tokens:** Stateless JWTs, cannot be revoked, short-lived (1 hour)
- **Refresh Tokens:** Stored in MongoDB, can be explicitly revoked, long-lived (30 days)

---

## Operational Monitoring

### Health Checks
```bash
curl http://localhost:8080/actuator/health
```

### Key Metrics to Monitor
- Active WebSocket connections per instance
- Redis Pub/Sub publish failures
- MongoDB connection pool utilization
- Message delivery latency
- Presence tracking set sizes in Redis

### Failure Modes

| Scenario | Impact | Recovery |
|----------|--------|----------|
| **Backend Instance Crash** | Clients reconnect to other instances | Automatic (load balancer) |
| **Redis Down** | No real-time delivery | Messages persist in MongoDB, clients poll |
| **MongoDB Down** | All operations fail | Requires manual intervention |
| **Client Disconnect** | Session removed from Redis | Client reconnects automatically |

---

## Contact

Feel free to clone this project and make your own changes.
For questions or issues, please open a GitHub issue or contact me directly.
