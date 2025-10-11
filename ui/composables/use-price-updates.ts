import { ref, onMounted, onUnmounted } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useToast } from './use-toast'
import type { PriceUpdateEvent } from '../models/generated/domain-models'

export function usePriceUpdates() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const isConnected = ref(false)
  const lastUpdate = ref<PriceUpdateEvent | null>(null)
  let eventSource: EventSource | null = null

  const connect = () => {
    if (eventSource || typeof EventSource === 'undefined') {
      return
    }

    const url = '/api/price-updates'
    eventSource = new EventSource(url)

    eventSource.onopen = () => {
      isConnected.value = true
      console.log('SSE connection established')
    }

    eventSource.addEventListener('price-update', (event: MessageEvent) => {
      try {
        const data: PriceUpdateEvent = JSON.parse(event.data)
        lastUpdate.value = data

        console.log('Received price update:', data)

        queryClient.invalidateQueries({ queryKey: ['instruments'] })
        queryClient.invalidateQueries({ queryKey: ['transactions'] })
        queryClient.invalidateQueries({ queryKey: ['summaries'] })

        if (data.type === 'coefficient') {
          toast.info(data.message)
        } else if (data.type === 'refresh') {
          toast.success(data.message)
        } else if (data.type === 'update' || data.type === 'price-fetch') {
          console.log('Price update:', data.message)
        }
      } catch (error) {
        console.error('Failed to parse SSE event:', error)
      }
    })

    eventSource.onerror = error => {
      console.error('SSE error:', error)
      isConnected.value = false

      eventSource?.close()
      eventSource = null

      setTimeout(() => {
        console.log('Attempting to reconnect SSE...')
        connect()
      }, 5000)
    }
  }

  const disconnect = () => {
    if (eventSource) {
      eventSource.close()
      eventSource = null
      isConnected.value = false
      console.log('SSE connection closed')
    }
  }

  onMounted(() => {
    connect()
  })

  onUnmounted(() => {
    disconnect()
  })

  return {
    isConnected,
    lastUpdate,
    connect,
    disconnect,
  }
}
