import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { Association, CreateAssociationRequest } from './association.model';

const ACTIVE_ASSOCIATION_KEY = 'bereta.activeAssociationId';

@Injectable({ providedIn: 'root' })
export class AssociationService {
  private readonly http = inject(HttpClient);
  readonly associations = signal<Association[]>([]);
  readonly activeAssociationId = signal<string | null>(this.readActiveId());
  readonly activeAssociation = signal<Association | null>(null);
  /** Association tout juste créée : mise en évidence quelques secondes sur le tableau de bord. */
  readonly lastCreatedId = signal<string | null>(null);

  list(): Observable<Association[]> {
    return this.http.get<Association[]>('/api/associations').pipe(tap((items) => {
      this.associations.set(items);
      const currentId = this.activeAssociationId();
      const current = items.find((item) => item.id === currentId) ?? null;
      this.activeAssociation.set(current);
      // Pas d'écran « Choisir une association » : la dernière choisie si elle est encore accessible,
      // sinon la première qu'on gère, sinon la première tout court (modifiable dans la barre du haut).
      if (!current) this.select((items.find((item) => item.currentUserResponsible) ?? items[0])?.id ?? null);
    }));
  }

  create(request: CreateAssociationRequest): Observable<Association> {
    return this.http.post<Association>('/api/associations', request).pipe(tap((created) => {
      this.associations.update((items) => [...items, created]);
      this.lastCreatedId.set(created.id);
      this.select(created.id);
    }));
  }

  select(id: string | null): void {
    this.activeAssociationId.set(id);
    this.activeAssociation.set(this.associations().find((item) => item.id === id) ?? null);
    if (id) localStorage.setItem(ACTIVE_ASSOCIATION_KEY, id);
    else localStorage.removeItem(ACTIVE_ASSOCIATION_KEY);
  }

  clear(): void {
    this.associations.set([]);
    this.activeAssociation.set(null);
    this.select(null);
  }

  private readActiveId(): string | null {
    try { return localStorage.getItem(ACTIVE_ASSOCIATION_KEY); }
    catch { return null; }
  }
}
