import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { apiErrorCode, apiErrorMessage } from './api-error';

describe('apiErrorMessage', () => {
  it('shows the French detail sent by the backend', () => {
    const error = new HttpErrorResponse({ status: 401, error: { detail: 'Identifiants incorrects.', code: 'INVALID_CREDENTIALS' } });
    expect(apiErrorMessage(error, 'repli')).toBe('Identifiants incorrects.');
    expect(apiErrorCode(error)).toBe('INVALID_CREDENTIALS');
  });

  it('falls back for generic Spring validation errors and unknown failures', () => {
    expect(apiErrorMessage(new HttpErrorResponse({ status: 400, error: { detail: 'Invalid request content.' } }), 'repli'))
      .toBe('repli');
    expect(apiErrorMessage(new Error('boom'), 'repli')).toBe('repli');
  });

  it('explains when the server cannot be reached', () => {
    expect(apiErrorMessage(new HttpErrorResponse({ status: 0 }), 'repli')).toContain('injoignable');
  });
});
