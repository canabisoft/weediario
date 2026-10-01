import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MyProfile } from '../core/api/api.models';
import { AuthService } from '../core/auth/auth.service';
import { ModerationPage } from './moderation.page';

function profile(role: string): MyProfile {
  return {
    id: 'u1', username: 'equipe', hasAvatar: false, role, hasRecoveryPhrase: true,
    termsAccepted: true, currentTermsVersion: '1.0', profileHidden: false,
  };
}

describe('ModerationPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  });

  function tabsFor(role: string, aba?: string): { labels: string[]; root: HTMLElement } {
    TestBed.inject(AuthService).me.set(profile(role));
    const fixture = TestBed.createComponent(ModerationPage);
    if (aba) {
      fixture.componentRef.setInput('aba', aba);
    }
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const labels = [...root.querySelectorAll('[role=tab]')].map((tab) => tab.textContent!.trim());
    return { labels, root };
  }

  it('shows metrics and team tabs only to admins', () => {
    expect(tabsFor('ADMIN').labels).toEqual(expect.arrayContaining(['Métricas', 'Equipe']));
  });

  it('hides admin tabs from moderators, even when the URL asks for them', () => {
    const { labels, root } = tabsFor('MODERATOR', 'equipe');
    expect(labels).not.toContain('Métricas');
    expect(labels).not.toContain('Equipe');
    expect(root.querySelector('app-team-panel')).toBeNull();
    expect(root.querySelector('app-content-queue')).not.toBeNull();
  });
});
