import { Client } from '@stomp/stompjs'
import { getAccessToken } from '../api/client'

class WebSocketService {
  constructor() {
    this.client = null
    this.connected = false
    this.subscriptions = new Map()
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectDelay = 2000
  }

  connect() {
    const token = getAccessToken()
    if (!token) {
      console.error('No access token available for WebSocket connection')
      return
    }

    const wsUrl = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws'

    this.client = new Client({
      brokerURL: `${wsUrl}?token=${token}`,
      reconnectDelay: this.reconnectDelay,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        console.log('WebSocket connected')
        this.connected = true
        this.reconnectAttempts = 0
        this.resubscribeAll()
      },

      onDisconnect: () => {
        console.log('WebSocket disconnected')
        this.connected = false
        this.subscriptions.clear()
      },

      onStompError: (frame) => {
        console.error('WebSocket STOMP error:', frame.headers.message)
        console.error('Details:', frame.body)
      },

      onWebSocketError: (error) => {
        console.error('WebSocket error:', error)
      }
    })

    this.client.activate()
  }

  disconnect() {
    if (this.client) {
      this.subscriptions.clear()
      this.client.deactivate()
      this.client = null
      this.connected = false
    }
  }

  subscribe(destination, callback) {
    if (!this.client || !this.connected) {
      console.warn('WebSocket not connected, storing subscription for later')
      this.subscriptions.set(destination, callback)
      return null
    }

    const subscription = this.client.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body)
        callback(data)
      } catch (error) {
        console.error('Error parsing WebSocket message:', error)
      }
    })

    this.subscriptions.set(destination, callback)
    return subscription
  }

  unsubscribe(destination) {
    this.subscriptions.delete(destination)
  }

  resubscribeAll() {
    const destinations = Array.from(this.subscriptions.entries())
    this.subscriptions.clear()

    destinations.forEach(([destination, callback]) => {
      this.subscribe(destination, callback)
    })
  }

  send(destination, body = {}) {
    if (!this.client || !this.connected) {
      console.error('WebSocket not connected, cannot send message')
      return
    }

    this.client.publish({
      destination,
      body: JSON.stringify(body)
    })
  }

  isConnected() {
    return this.connected
  }

  subscribeToUnreadUpdates(callback) {
    return this.subscribe('/user/queue/unread', callback)
  }

  subscribeToSeenEvents(callback) {
    return this.subscribe('/user/queue/seen', callback)
  }
}

export default new WebSocketService()
