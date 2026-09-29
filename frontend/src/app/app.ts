import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MainLayout } from './layout/main-layout';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MainLayout],
  template: `<app-main-layout />`,
})
export class App {}
