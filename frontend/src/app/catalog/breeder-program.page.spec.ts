import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { MyProfile } from '../core/api/api.models';
import { BreederProgramApi } from '../core/api/breeder-program.api';
import { AuthService } from '../core/auth/auth.service';
import { BreederProgramPage } from './breeder-program.page';

const ME: MyProfile = {
  id: 'u1', username: 'quintal', hasAvatar: false, hasRecoveryPhrase: true, role: 'GROWER', termsAccepted: true,
  currentTermsVersion: '1.0', profileHidden: false,
};

describe('BreederProgramPage', () => {
  let calls = 0;
  const requests: unknown[][] = [];
  const credits: unknown[][] = [];
  const api: Partial<BreederProgramApi> = {
    mine: () => {
      calls++;
      return of({
        representations: [
          { breederId: 'b1', breeder: 'Quintal Genetics', status: 'APPROVED', requestedAt: '2026-09-01T00:00:00Z',
            decidedAt: '2026-09-02T00:00:00Z', claimedKind: 'INDEPENDENT', publicCredit: false },
        ],
        strains: [],
      });
    },
    requestRepresentation: (...args: unknown[]) => {
      requests.push(args);
      return of(undefined);
    },
    changeCredit: (...args: unknown[]) => {
      credits.push(args);
      return of(undefined);
    },
  } as Partial<BreederProgramApi>;

  beforeEach(() => {
    calls = 0;
    requests.length = 0;
    credits.length = 0;
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: BreederProgramApi, useValue: api }],
    });
  });

  async function render(loggedIn = true) {
    if (loggedIn) {
      TestBed.inject(AuthService).me.set(ME);
    }
    const fixture = TestBed.createComponent(BreederProgramPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  it('presents the program and the rankings to visitors, asking them to sign in only to join', async () => {
    const { root } = await render(false);

    expect(root.textContent).toContain('Programa de breeders');
    expect(root.textContent).toContain('Ranking de strains');
    expect(root.textContent).toContain('Ranking de breeders');
    expect(root.querySelector('a[href="/mais-usados?tipo=PRODUCTS"]')).not.toBeNull();
    expect(root.querySelector('a[href="/entrar?voltar=%2Fbreeders%2Fprograma"]')).not.toBeNull();
    expect(root.textContent).not.toContain('Pedir para entrar no programa');
    expect(calls).toBe(0);
  });

  it('explains the program for independent breeders and lets them opt into credit', async () => {
    const { fixture, root } = await render();
    expect(root.textContent).toContain('breeders independentes');
    expect(root.textContent).toContain('Breeder independente');

    const credit = [...root.querySelectorAll<HTMLInputElement>('li input[type=checkbox]')][0];
    expect(credit.checked).toBe(false);
    credit.click();
    fixture.detectChanges();
    expect(credits).toEqual([['b1', true]]);
  });

  it('only offers public credit when requesting as an independent breeder', async () => {
    const { fixture, root } = await render();
    const creditOption = () => root.querySelector('input[name=publicCredit]');
    expect(creditOption()).toBeNull();

    const independent = [...root.querySelectorAll('label')].find((l) => l.textContent?.includes('Breeder independente') && l.querySelector('input[type=radio]'))!;
    independent.querySelector<HTMLInputElement>('input')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(creditOption()).not.toBeNull();
    expect(root.textContent).toContain('Nome do seu projeto de breeding');
  });
});
