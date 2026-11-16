/** Contrats de l'API d'import de membres (/api/imports). */

export type ImportStatus = 'BROUILLON' | 'VALIDE' | 'ANNULE';
export type ImportRowStatus = 'PRETE' | 'A_VERIFIER' | 'DOUBLON';
export type ImportSourceType = 'CSV' | 'EXCEL' | 'PDF' | 'PHOTO';
export type DuplicateAction = 'IGNORER' | 'CREER';
export type Confidence = 'HAUTE' | 'MOYENNE' | 'BASSE';

/** Caisse de destination : localFundId null = caisse principale. */
export interface ImportFund {
  primaryFundId: string;
  localFundId: string | null;
  name: string;
  primary: boolean;
}

/** Membre existant proposé comme doublon ; nom masqué s'il appartient à une caisse non visible. */
export interface ExistingMember {
  id: string | null;
  firstName: string | null;
  lastName: string | null;
  fundName: string | null;
  visible: boolean;
}

export interface ImportRow {
  id: string;
  lineNumber: number;
  readFirstName: string | null;
  readLastName: string | null;
  readPhone: string | null;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  phoneE164: string | null;
  confidence: Confidence | null;
  remark: string | null;
  status: ImportRowStatus;
  issues: string[];
  /** Aucun problème bloquant : « Ligne vérifiée » suffit. Sinon, il faut corriger ou exclure la ligne. */
  confirmable: boolean;
  included: boolean;
  confirmed: boolean;
  duplicateMember: ExistingMember | null;
  duplicateOfLine: number | null;
  duplicateAction: DuplicateAction | null;
}

export interface ImportCounters {
  total: number;
  ready: number;
  toCheck: number;
  duplicates: number;
  excluded: number;
  toCreate: number;
  ignored: number;
}

export interface ImportPreview {
  id: string;
  status: ImportStatus;
  sourceType: ImportSourceType;
  fund: ImportFund;
  createdAt: string;
  alreadyImportedAt: string | null;
  currentCampaignName: string | null;
  counters: ImportCounters;
  rows: ImportRow[];
}

/** Ligne envoyée au serveur : sans id = ligne ajoutée ; une ligne absente de la liste est supprimée. */
export interface ImportRowUpdate {
  id: string | null;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  included: boolean;
  confirmed: boolean;
  duplicateAction: DuplicateAction | null;
}

export interface ImportValidation {
  id: string;
  created: number;
  ignored: number;
  enrolled: number;
  campaignName: string | null;
  fund: ImportFund;
}
