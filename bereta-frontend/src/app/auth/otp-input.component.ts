import {
  AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, QueryList, ViewChildren, signal,
} from '@angular/core';

/** Saisie d'un code à 6 chiffres : une case par chiffre, collage du code complet, retour arrière. */
@Component({
  selector: 'app-otp-input',
  standalone: true,
  template: `
    <div class="otp-boxes" role="group" [attr.aria-label]="label">
      @for (index of indexes; track index) {
        <input #box class="otp-box" type="text" inputmode="numeric" pattern="[0-9]*" maxlength="6"
               [attr.autocomplete]="index === 0 ? 'one-time-code' : 'off'"
               [attr.aria-label]="'Chiffre ' + (index + 1) + ' sur 6'"
               [attr.aria-invalid]="invalid" [value]="digits()[index]" [disabled]="disabled"
               (input)="onInput(index, $event)" (keydown)="onKeydown(index, $event)"
               (paste)="onPaste($event)" (focus)="selectAll($event)" />
      }
    </div>
  `,
  styles: `
    .otp-boxes { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 8px; margin: 6px 0 4px; }
    .otp-box {
      width: 100%; min-width: 0; height: 56px; padding: 0; text-align: center; font-size: 24px; font-weight: 700;
      border: 1.5px solid #cbd5e1; border-radius: 8px; background: #fff; color: #0f172a;
    }
    .otp-box:focus { outline: none; border: 2px solid #166534; box-shadow: 0 0 0 3px rgba(22, 101, 52, .15); }
    .otp-box[aria-invalid='true'] { border-color: #dc2626; }
  `,
})
export class OtpInputComponent implements AfterViewInit {
  @Input() label = 'Code à 6 chiffres';
  @Input() disabled = false;
  @Input() invalid = false;
  @Output() readonly codeChange = new EventEmitter<string>();
  @Output() readonly completed = new EventEmitter<string>();
  @ViewChildren('box') private boxes!: QueryList<ElementRef<HTMLInputElement>>;

  protected readonly indexes = [0, 1, 2, 3, 4, 5];
  protected readonly digits = signal<string[]>(['', '', '', '', '', '']);

  ngAfterViewInit(): void {
    setTimeout(() => this.focus(0));
  }

  clear(): void {
    this.digits.set(['', '', '', '', '', '']);
    this.boxes.forEach((box) => (box.nativeElement.value = ''));
    this.focus(0);
    this.codeChange.emit('');
  }

  protected onInput(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const typed = input.value.replace(/\D/g, '');
    if (typed.length > 1) {
      this.fill(index, typed);
      return;
    }
    input.value = typed;
    this.digits.update((current) => current.map((digit, i) => (i === index ? typed : digit)));
    if (typed && index < 5) this.focus(index + 1);
    this.emit();
  }

  protected onKeydown(index: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace' && !this.digits()[index] && index > 0) {
      event.preventDefault();
      this.digits.update((current) => current.map((digit, i) => (i === index - 1 ? '' : digit)));
      const previous = this.boxes.get(index - 1)?.nativeElement;
      if (previous) previous.value = '';
      this.focus(index - 1);
      this.emit();
    } else if (event.key === 'ArrowLeft' && index > 0) {
      this.focus(index - 1);
    } else if (event.key === 'ArrowRight' && index < 5) {
      this.focus(index + 1);
    }
  }

  protected onPaste(event: ClipboardEvent): void {
    const pasted = (event.clipboardData?.getData('text') ?? '').replace(/\D/g, '');
    if (!pasted) return;
    event.preventDefault();
    this.fill(0, pasted);
  }

  protected selectAll(event: FocusEvent): void {
    (event.target as HTMLInputElement).select();
  }

  private fill(start: number, value: string): void {
    const next = [...this.digits()];
    value.slice(0, 6 - start).split('').forEach((digit, offset) => (next[start + offset] = digit));
    this.digits.set(next);
    this.boxes.forEach((box, i) => (box.nativeElement.value = next[i]));
    this.focus(Math.min(start + value.length, 5));
    this.emit();
  }

  private focus(index: number): void {
    this.boxes?.get(index)?.nativeElement.focus();
  }

  private emit(): void {
    const code = this.digits().join('');
    this.codeChange.emit(code);
    if (/^\d{6}$/.test(code)) this.completed.emit(code);
  }
}
