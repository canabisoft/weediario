/** Evento Server-Sent Events já separado em nome e dados. */
export interface SseEvent {
  readonly event: string;
  readonly data: string;
}

/**
 * Separa os eventos completos de um trecho do stream (formato text/event-stream). O que sobrar incompleto
 * volta em {@code rest}, para juntar com o próximo pedaço. Comentários (heartbeat) são ignorados.
 */
export function parseSse(buffer: string): { events: SseEvent[]; rest: string } {
  const normalized = buffer.replace(/\r\n?/g, '\n');
  const blocks = normalized.split('\n\n');
  const rest = blocks.pop() ?? '';
  const events: SseEvent[] = [];
  for (const block of blocks) {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (!line || line.startsWith(':')) {
        continue;
      }
      const colon = line.indexOf(':');
      const field = colon === -1 ? line : line.slice(0, colon);
      const value = colon === -1 ? '' : line.slice(colon + 1).replace(/^ /, '');
      if (field === 'event') {
        event = value;
      } else if (field === 'data') {
        data.push(value);
      }
    }
    if (data.length) {
      events.push({ event, data: data.join('\n') });
    }
  }
  return { events, rest };
}
