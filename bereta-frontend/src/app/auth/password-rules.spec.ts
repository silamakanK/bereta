import { describe, expect, it } from 'vitest';
import { passwordStrength, passwordValidationMessage } from './password-rules';

describe('passwordValidationMessage', () => {
  it('rejects passwords shorter than 8 characters', () => {
    expect(passwordValidationMessage('awa1234', 'awa1234')).toContain('8 caractères');
  });

  it('requires at least one digit', () => {
    expect(passwordValidationMessage('motdepasse', 'motdepasse')).toContain('1 chiffre');
  });

  it('accepts the mockup rule: 8 characters including a digit', () => {
    expect(passwordValidationMessage('bereta2025', 'bereta2025')).toBeNull();
  });

  it('rejects mismatched confirmation', () => {
    expect(passwordValidationMessage('bereta2025', 'bereta2026')).toContain('ne correspondent pas');
  });

  it('enforces the BCrypt UTF-8 byte limit', () => {
    expect(passwordValidationMessage('é'.repeat(37) + '1', 'é'.repeat(37) + '1')).toContain('72 octets');
  });
});

describe('passwordStrength', () => {
  it('grades empty, insufficient, valid and strong passwords', () => {
    expect(passwordStrength('')).toBe(0);
    expect(passwordStrength('abc')).toBe(1);
    expect(passwordStrength('bereta2025')).toBe(2);
    expect(passwordStrength('Bereta-2025')).toBe(3);
  });
});
