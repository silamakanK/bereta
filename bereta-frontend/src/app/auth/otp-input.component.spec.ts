import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { OtpInputComponent } from './otp-input.component';

function setup() {
  const fixture = TestBed.createComponent(OtpInputComponent);
  fixture.detectChanges();
  const boxes = Array.from(fixture.nativeElement.querySelectorAll('input')) as HTMLInputElement[];
  const completed: string[] = [];
  fixture.componentInstance.completed.subscribe((code) => completed.push(code));
  return { fixture, boxes, completed };
}

describe('OtpInputComponent', () => {
  it('renders six numeric boxes with one-time-code autofill on the first', () => {
    const { boxes } = setup();
    expect(boxes).toHaveLength(6);
    expect(boxes[0].getAttribute('autocomplete')).toBe('one-time-code');
    expect(boxes.every((box) => box.inputMode === 'numeric')).toBe(true);
  });

  it('fills every box and completes when a full code is pasted', () => {
    const { fixture, boxes, completed } = setup();
    const event = new Event('paste', { cancelable: true }) as ClipboardEvent;
    Object.defineProperty(event, 'clipboardData', { value: { getData: () => ' 12 34-56 ' } });
    boxes[0].dispatchEvent(event);
    fixture.detectChanges();
    expect(boxes.map((box) => box.value).join('')).toBe('123456');
    expect(completed).toEqual(['123456']);
  });

  it('ignores letters and completes after six typed digits', () => {
    const { fixture, boxes, completed } = setup();
    boxes[0].value = 'a';
    boxes[0].dispatchEvent(new Event('input'));
    expect(completed).toEqual([]);
    '987654'.split('').forEach((digit, index) => {
      boxes[index].value = digit;
      boxes[index].dispatchEvent(new Event('input'));
    });
    fixture.detectChanges();
    expect(completed).toEqual(['987654']);
  });

  it('moves back and clears the previous digit on backspace in an empty box', () => {
    const { fixture, boxes } = setup();
    boxes[0].value = '4';
    boxes[0].dispatchEvent(new Event('input'));
    boxes[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Backspace' }));
    fixture.detectChanges();
    expect(boxes[0].value).toBe('');
  });
});
