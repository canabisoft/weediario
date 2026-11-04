import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MainLayout } from './layout/main-layout';
import { SeoService } from './seo/seo.service';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MainLayout],
  template: `<app-main-layout />`,
})
export class App {
  constructor() {
    // Head de cada página (title, description, canonical, og:*, JSON-LD), também na pré-renderização do build.
    inject(SeoService);
  }
}
