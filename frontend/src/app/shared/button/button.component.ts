// button.component.ts
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-button',
  template: `<button [class]="color">{{ label }}</button>`,
  styleUrls: ['./button.component.css']
})
export class ButtonComponent {
  @Input() label = '';
  @Input() color: 'primary' | 'secondary' = 'primary';
}
