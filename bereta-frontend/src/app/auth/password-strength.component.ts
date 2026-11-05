import { Component, Input } from '@angular/core';
import { PASSWORD_RULE, passwordStrength } from './password-rules';

/** Jauge discrète : la règle tant que rien n'est saisi, puis le niveau atteint. */
@Component({
  selector: 'app-password-strength',
  standalone: true,
  template: `
    <div class="strength" [attr.data-level]="level()">
      <div class="strength-bars" aria-hidden="true"><span></span><span></span><span></span></div>
      <span class="strength-text">{{ level() === 0 ? rule : labels[level()] }}</span>
    </div>
  `,
  styles: `
    .strength { display: grid; gap: 6px; margin: 6px 0 4px; font-size: 14px; color: #64748b; }
    .strength-bars { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; }
    .strength-bars span { height: 4px; border-radius: 99px; background: #e2e8f0; }
    [data-level='1'] .strength-bars span:nth-child(-n + 1) { background: #dc2626; }
    [data-level='2'] .strength-bars span:nth-child(-n + 2) { background: #d97706; }
    [data-level='3'] .strength-bars span { background: #15803d; }
    [data-level='1'] .strength-text { color: #b91c1c; }
  `,
})
export class PasswordStrengthComponent {
  @Input() password = '';
  protected readonly rule = PASSWORD_RULE;
  protected readonly labels = ['', 'Trop faible : 8 caractères et 1 chiffre', 'Mot de passe correct', 'Mot de passe solide'];

  protected level(): 0 | 1 | 2 | 3 {
    return passwordStrength(this.password);
  }
}
