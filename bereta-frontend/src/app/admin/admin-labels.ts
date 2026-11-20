import { AdminRole } from './admin.model';

/** Libellés partagés de l'espace administrateur (statuts : pastille + icône + libellé, jamais la couleur seule). */
export const ROLE_LABELS: Record<AdminRole, string> = {
  ADMIN: 'Administrateur',
  RESPONSABLE: 'Responsable',
  TRESORIER: 'Trésorier',
};

export function accountStatus(enabled: boolean): { css: string; icon: string; label: string } {
  return enabled ? { css: 'ok', icon: '✓', label: 'Actif' } : { css: 'off', icon: '×', label: 'Désactivé' };
}

export const ACTION_LABELS: Record<string, string> = {
  USER_DISABLED: 'Compte désactivé',
  USER_ENABLED: 'Compte réactivé',
  USER_PASSWORD_RESET: 'Mot de passe provisoire',
  ASSOCIATION_ARCHIVED: 'Association archivée',
  ASSOCIATION_CREATED: 'Association créée',
  TREASURER_ASSIGNED: 'Trésorier désigné',
  TREASURER_REMOVED: 'Trésorier retiré',
  ADMIN_PROMOTED_BY_PROCEDURE: 'Promotion administrateur',
};

export function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action;
}

/** « enabled=false, closedSessions=2 » pour l'affichage des valeurs avant / après. */
export function describeValues(values: Record<string, unknown> | null): string {
  if (!values) return '—';
  const entries = Object.entries(values);
  return entries.length ? entries.map(([key, value]) => `${key} : ${String(value)}`).join(', ') : '—';
}
