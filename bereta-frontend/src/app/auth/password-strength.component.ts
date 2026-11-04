import { Component, Input } from '@angular/core';
import { PASSWORD_RULE, passwordStrength } from './password-rules';

@Component({
  selector: 'app-password-strength',
  standalone: true,
  template: `
    <div class="strength" [attr.data-level]="level()">
      <div class="strength-head">
        <span>Force du mot de passe :</span><strong>{{ labels[level()] }}</strong>
      </div>
      <div class="strength-bars" aria-hidden="true"><span></span><span></span><span></span></div>
      <p>{{ rule }}</p>
    </div>
  `,
  styles: `
    .strength { margin: 4px 0 12px; padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 14px; }
    .strength-head { display: flex; justify-content: space-between; color: #475569; }
    .strength-bars { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin: 8px 0; }
    .strength-bars span { height: 6px; border-radius: 99px; background: #e2e8f0; }
    .strength p { margin: 0; color: #475569; }
    [data-level='1'] .strength-bars span:nth-child(-n + 1) { background: #dc2626; }
    [data-level='2'] .strength-bars span:nth-child(-n + 2) { background: #d97706; }
    [data-level='3'] .strength-bars span { background: #15803d; }
  `,
})
export class PasswordStrengthComponent {
  @Input() password = '';
  protected readonly rule = PASSWORD_RULE;
  protected readonly labels = ['À renseigner', 'Insuffisant', 'Conforme', 'Solide'];

  protected level(): 0 | 1 | 2 | 3 {
    return passwordStrength(this.password);
  }
}
