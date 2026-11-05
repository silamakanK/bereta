export interface Association {
  id: string;
  name: string;
  description: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  address: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
  currentUserResponsible: boolean;
  createdAt: string;
}

export interface CreateAssociationRequest {
  name: string;
  description?: string;
  contactEmail?: string;
  contactPhone?: string;
  address?: string;
  /** Objectif principal de la caisse principale créée avec l'association. */
  objective?: string;
}
