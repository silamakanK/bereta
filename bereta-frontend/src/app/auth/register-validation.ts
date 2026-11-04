export function passwordValidationMessage(password: string, confirmation: string): string | null {
  if (password.length < 6) return 'Le mot de passe doit contenir au moins 6 caractères.';
  if (new TextEncoder().encode(password).length > 72) {
    return 'Le mot de passe ne peut pas dépasser 72 octets UTF-8.';
  }
  if (password !== confirmation) return 'Les deux mots de passe ne correspondent pas.';
  return null;
}
