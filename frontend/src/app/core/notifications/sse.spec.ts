import { parseSse } from './sse';

describe('parseSse', () => {
  it('splits complete events and keeps the incomplete tail for the next chunk', () => {
    const first = parseSse('event:unread\ndata:{"unread":2}\n\n:hb\n\nevent:unr');
    expect(first.events).toEqual([{ event: 'unread', data: '{"unread":2}' }]);
    expect(first.rest).toBe('event:unr');

    const second = parseSse(first.rest + 'ead\ndata: {"unread":3}\n\n');
    expect(second.events).toEqual([{ event: 'unread', data: '{"unread":3}' }]);
    expect(second.rest).toBe('');
  });

  it('ignores heartbeats and handles CRLF and multi-line data', () => {
    const { events } = parseSse(':hb\r\n\r\ndata:a\r\ndata:b\r\n\r\n');
    expect(events).toEqual([{ event: 'message', data: 'a\nb' }]);
  });
});
