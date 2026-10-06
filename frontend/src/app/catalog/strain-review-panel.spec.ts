import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MyProfile } from '../core/api/api.models';
import { AuthService } from '../core/auth/auth.service';
import { StrainReviewPanel } from './strain-review-panel';

const ME: MyProfile = {
  id: 'u1', username: 'maria', hasAvatar: false, hasRecoveryPhrase: true, role: 'GROWER', termsAccepted: true,
  currentTermsVersion: '1.0', profileHidden: false,
};

function setup(loggedIn: boolean) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  if (loggedIn) {
    TestBed.inject(AuthService).me.set(ME);
  }
  const fixture = TestBed.createComponent(StrainReviewPanel);
  fixture.componentRef.setInput('strainId', 's1');
  fixture.componentRef.setInput('strainName', 'Gelato 33');
  fixture.detectChanges();
  return { fixture, http: TestBed.inject(HttpTestingController), root: fixture.nativeElement as HTMLElement };
}

describe('StrainReviewPanel', () => {
  it('asks visitors to sign in, without asking the server about their grows', () => {
    const { root, http } = setup(false);

    expect(root.textContent).toContain('Avaliar Gelato 33');
    expect(root.querySelector('a[href^="/entrar"]')).not.toBeNull();
    http.verify();
  });

  it('leads growers who never grew the strain to a new diary with it', () => {
    const { fixture, root, http } = setup(true);
    http.expectOne('/api/v1/me/strains/s1/grows').flush([]);
    fixture.detectChanges();

    expect(root.textContent).toContain('só avalia quem cultivou');
    expect(root.querySelector('a[href="/diarios/novo?strainId=s1"]')).not.toBeNull();
  });

  it('opens the review form for a completed grow that has no review yet', () => {
    const { fixture, root, http } = setup(true);
    http.expectOne('/api/v1/me/strains/s1/grows').flush([
      { diaryId: 'd2', title: 'Ciclo 2', completed: false, reviewed: false },
      { diaryId: 'd1', title: 'Ciclo 1', completed: true, reviewed: false },
    ]);
    fixture.detectChanges();
    expect(root.textContent).toContain('Você concluiu um cultivo desta strain');

    root.querySelector<HTMLButtonElement>('button.wd-button')!.click();
    http.expectOne('/api/v1/diaries/d1').flush({ id: 'd1', strainName: 'Gelato 33', breeder: 'Barneys' });
    fixture.detectChanges();

    expect(root.querySelector('app-review-form')).not.toBeNull();
    http.verify();
  });

  it('tells who is still growing it to come back after the harvest', () => {
    const { fixture, root, http } = setup(true);
    http.expectOne('/api/v1/me/strains/s1/grows').flush([{ diaryId: 'd2', title: 'Ciclo 2', completed: false, reviewed: false }]);
    fixture.detectChanges();

    expect(root.textContent).toContain('Quando concluir o cultivo, a avaliação abre aqui');
  });
});
