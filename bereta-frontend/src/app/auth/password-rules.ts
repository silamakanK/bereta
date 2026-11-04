/** Règle de la maquette, identique au backend (PasswordPolicy). */
export const PASSWORD_RULE = 'Au moins 8 caractères, dont 1 chiffre.';

export function passwordValidationMessage(password: string, confirmation: string): string | null {
  if (password.length < 8) return 'Le mot de passe doit contenir au moins 8 caractères.';
  if (!/\d/.test(password)) return 'Le mot de passe doit contenir au moins 1 chiffre.';
  if (new TextEncoder().encode(password).length > 72) {
    return 'Le mot de passe ne peut pas dépasser 72 octets.';
  }
  if (password !== confirmation) return 'Les deux mots de passe ne correspondent pas.';
  return null;
}

/** 0 = vide, 1 = insuffisant, 2 = conforme, 3 = solide (indicatif, pour la jauge). */
export function passwordStrength(password: string): 0 | 1 | 2 | 3 {
  if (!password) return 0;
  if (password.length < 8 || !/\d/.test(password)) return 1;
  const variety = [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter((rule) => rule.test(password)).length;
  return password.length >= 12 || variety >= 3 ? 3 : 2;
}
