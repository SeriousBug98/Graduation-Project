import { useEffect, useRef } from 'react'

export function useEventStream<T = any>(url: string, onEvent: (data: T) => void) {
  const handlerRef = useRef(onEvent)
  handlerRef.current = onEvent

  useEffect(() => {
    const es = new EventSource(url, { withCredentials: true })
    es.onmessage = (e) => {
      try {
        const parsed = JSON.parse(e.data)
        handlerRef.current(parsed)
      } catch (_) {
        // ignore malformed
      }
    }
    es.onerror = () => {
      // 브라우저가 자동 재시도. 필요시 상태 표시만.
    }
    return () => es.close()
  }, [url])
}