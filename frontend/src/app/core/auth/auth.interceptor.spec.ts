import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor + AuthService', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => backend.verify());

  async function loginAs(token: string): Promise<void> {
    const login = auth.login('a@example.org', 'senha-bem-longa-123');
    backend.expectOne('/api/v1/auth/login').flush({ accessToken: token, expiresAt: '2030-01-01T00:00:00Z' });
    await Promise.resolve();
    backend.expectOne('/api/v1/me').flush({ id: '1', username: 'grower', email: 'a@example.org', hasAvatar: false, role: 'GROWER' });
    await login;
  }

  it('keeps the access token only in memory and sends it as Bearer', async () => {
    await loginAs('token-1');

    const request = firstValueFrom(http.get('/api/v1/me/setups'));
    const sent = backend.expectOne('/api/v1/me/setups');
    expect(sent.request.headers.get('Authorization')).toBe('Bearer token-1');
    sent.flush([]);
    await request;

    expect(localStorage.length + sessionStorage.length).toBe(0);
  });

  it('never sends the Bearer to auth endpoints or outside the API', async () => {
    await loginAs('token-1');

    firstValueFrom(http.post('/api/v1/auth/logout', {})).catch(() => undefined);
    expect(backend.expectOne('/api/v1/auth/logout').request.headers.has('Authorization')).toBe(false);

    firstValueFrom(http.get('https://outro-site.example/x')).catch(() => undefined);
    expect(backend.expectOne('https://outro-site.example/x').request.headers.has('Authorization')).toBe(false);
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    await loginAs('expired');

    const result = firstValueFrom(http.get<string[]>('/api/v1/me/setups'));
    backend.expectOne('/api/v1/me/setups').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').flush({ accessToken: 'fresh', expiresAt: '2030-01-01T00:00:00Z' });
    const retry = backend.expectOne('/api/v1/me/setups');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer fresh');
    retry.flush(['ok']);

    expect(await result).toEqual(['ok']);
  });

  it('logs out locally when the refresh also fails', async () => {
    await loginAs('expired');

    const result = firstValueFrom(http.get('/api/v1/me/setups')).catch((error) => error.status);
    backend.expectOne('/api/v1/me/setups').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(await result).toBe(401);
    expect(auth.isLoggedIn()).toBe(false);
    expect(auth.accessToken()).toBeNull();
  });

  it('shares one refresh between concurrent 401s (the backend treats reuse as theft)', async () => {
    await loginAs('expired');

    const first = firstValueFrom(http.get('/api/v1/a'));
    const second = firstValueFrom(http.get('/api/v1/b'));
    backend.expectOne('/api/v1/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/b').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').flush({ accessToken: 'fresh', expiresAt: '2030-01-01T00:00:00Z' });
    backend.expectOne('/api/v1/a').flush('a');
    backend.expectOne('/api/v1/b').flush('b');

    expect(await Promise.all([first, second])).toEqual(['a', 'b']);
  });
});
