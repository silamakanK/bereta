import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ImportService } from './import.service';

describe('ImportService', () => {
  let service: ImportService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ImportService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends the files as multipart, with the local fund when one is chosen', () => {
    service.analyse([{ blob: new Blob(['a;b']), name: 'liste.csv' }], 'fund-1').subscribe();
    const request = http.expectOne('/api/imports/membres/analyse');
    expect(request.request.method).toBe('POST');
    const form = request.request.body as FormData;
    expect((form.get('fichiers') as File).name).toBe('liste.csv');
    expect(form.get('localFundId')).toBe('fund-1');
    request.flush({});
  });

  it('omits the local fund for the primary fund', () => {
    service.analyse([{ blob: new Blob(['x']), name: 'p.jpg' }, { blob: new Blob(['y']), name: 'q.jpg' }], null).subscribe();
    const form = http.expectOne('/api/imports/membres/analyse').request.body as FormData;
    expect(form.getAll('fichiers')).toHaveLength(2);
    expect(form.has('localFundId')).toBe(false);
  });

  it('updates rows, validates and cancels a draft', () => {
    service.updateRows('imp-1', []).subscribe();
    const put = http.expectOne('/api/imports/imp-1/lignes');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ rows: [] });
    put.flush({});

    service.validate('imp-1', true).subscribe();
    const post = http.expectOne('/api/imports/imp-1/valider');
    expect(post.request.body).toEqual({ enrollInCurrentCampaign: true });
    post.flush({});

    service.cancel('imp-1').subscribe();
    expect(http.expectOne('/api/imports/imp-1').request.method).toBe('DELETE');
  });
});
