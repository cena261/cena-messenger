# Chat App Frontend

Vue 3 frontend for the real-time chat application.

## Tech Stack

- Vue 3
- Vite
- Pinia (state management)
- Vue Router
- STOMP over WebSocket
- Axios

## Project Structure

```
src/
├── api/              # REST API clients
│   ├── client.js     # Base axios client with auth interceptors
│   ├── auth.js       # Authentication endpoints
│   ├── conversations.js
│   └── messages.js
├── services/         # WebSocket and other services
│   └── websocket.js  # STOMP WebSocket service
├── stores/           # Pinia stores
│   ├── auth.js       # Authentication state
│   ├── conversations.js
│   ├── messages.js
│   └── realtime.js   # WebSocket subscriptions and realtime events
├── views/            # Page components
│   ├── LoginView.vue
│   ├── ConversationsView.vue
│   └── ChatView.vue
├── router/
│   └── index.js
└── main.js
```

## Setup

1. Install dependencies:
```bash
npm install
```

2. Configure environment variables:

Create a `.env` file (already created with defaults):
```
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_URL=ws://localhost:8080/ws
```

3. Start the development server:
```bash
npm run dev
```

The frontend will be available at `http://localhost:5173`

## Backend Requirements

The backend must be running at `http://localhost:8080` with the following endpoints:

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login
- `POST /api/auth/logout` - Logout
- `POST /api/auth/refresh` - Refresh access token
- `GET /api/auth/me` - Get current user

### Conversations
- `GET /api/conversations` - Get all conversations
- `GET /api/conversations/{id}` - Get conversation details
- `POST /api/conversations` - Create conversation
- `POST /api/conversations/{id}/read` - Mark conversation as read

### Messages
- `GET /api/messages?conversationId={id}` - Get messages
- `POST /api/messages` - Send message

### WebSocket
- `ws://localhost:8080/ws?token={accessToken}` - WebSocket connection with STOMP

## Authentication Flow

1. User logs in via `/api/auth/login`
2. Access token is stored in memory (not localStorage)
3. Refresh token is stored in HTTP-only cookie by backend
4. Access token is attached to all API requests via Authorization header
5. On 401, frontend attempts token refresh via `/api/auth/refresh`
6. WebSocket connection includes access token in URL query parameter

## WebSocket Subscriptions

After authentication and WebSocket connection:

- `/user/queue/unread` - Unread count updates
- `/user/queue/seen` - Read receipt events
- `/user/queue/typing` - Typing indicator events
- `/topic/conversation.{conversationId}` - Messages for specific conversation

## Typing Indicators

When user types in the message input:
- Sends `/app/typing/start` with `{conversationId}`
- After 3 seconds of inactivity, sends `/app/typing/stop`

## Development Notes

- Access tokens are stored only in memory for security
- Refresh tokens use HTTP-only cookies (set by backend)
- WebSocket reconnects automatically on disconnect
- Subscriptions are re-established after reconnect
- No complex UI frameworks - basic styling only
- No advanced features (editing, deletion, media uploads, etc.)
- Focus is on validating backend behavior, not polished UI

## Testing Backend Integration

1. Start backend on port 8080
2. Start frontend: `npm run dev`
3. Register a new user
4. Login
5. Open browser console to see WebSocket connection logs
6. Create conversations and send messages
7. Open multiple browser windows/tabs to test realtime delivery
8. Check unread counts, typing indicators, and seen receipts

## Build for Production

```bash
npm run build
```

Output will be in the `dist/` directory.
