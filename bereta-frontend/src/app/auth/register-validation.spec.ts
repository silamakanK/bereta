import { describe, expect, it } from 'vitest';
import { passwordValidationMessage } from './register-validation';

describe('passwordValidationMessage', () => {
  it('rejects passwords that are too short', () => {
    expect(passwordValidationMessage('short', 'short')).toContain('6 caractères');
  });

  it('accepts a matching six-character password', () => {
    expect(passwordValidationMessage('Awa123', 'Awa123')).toBeNull();
  });

  it('rejects mismatched confirmation', () => {
    expect(passwordValidationMessage('StrongPassword!234', 'DifferentPassword!')).toContain('ne correspondent pas');
  });

  it('enforces BCrypt UTF-8 byte limit', () => {
    expect(passwordValidationMessage('é'.repeat(37), 'é'.repeat(37))).toContain('72 octets');
  });

  it('accepts matching passwords within the requirements', () => {
    expect(passwordValidationMessage('StrongPassword!234', 'StrongPassword!234')).toBeNull();
  });
});
