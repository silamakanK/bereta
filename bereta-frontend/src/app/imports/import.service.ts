import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ImportPreview, ImportRowUpdate, ImportValidation } from './import.model';

/** Fichier prêt à l'envoi (photo compressée ou document tel quel). */
export interface ImportUpload {
  blob: Blob;
  name: string;
}

@Injectable({ providedIn: 'root' })
export class ImportService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/imports';

  /** localFundId null = caisse principale (responsable uniquement). */
  analyse(files: ImportUpload[], localFundId: string | null): Observable<ImportPreview> {
    const form = new FormData();
    for (const file of files) form.append('fichiers', file.blob, file.name);
    if (localFundId) form.append('localFundId', localFundId);
    return this.http.post<ImportPreview>(`${this.endpoint}/membres/analyse`, form);
  }

  updateRows(id: string, rows: ImportRowUpdate[]): Observable<ImportPreview> {
    return this.http.put<ImportPreview>(`${this.endpoint}/${id}/lignes`, { rows });
  }

  validate(id: string, enrollInCurrentCampaign: boolean): Observable<ImportValidation> {
    return this.http.post<ImportValidation>(`${this.endpoint}/${id}/valider`, { enrollInCurrentCampaign });
  }

  cancel(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${id}`);
  }
}
