export type Role = 'RESPONSABLE' | 'TRESORIER' | 'ADMIN';

export interface AuthUser {
  id: string;
  displayName: string | null;
  email: string | null;
  phone: string | null;
  role: Role;
  mustChangePassword: boolean;
}

/** Inscription et connexion renvoient directement la session (cookie de rafraîchissement en plus). */
export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresInSeconds: number;
  user: AuthUser;
}

/** Téléphone + mot de passe + CGU ; prénom, nom et e-mail ensuite (« Complète ton profil »). */
export interface RegisterRequest {
  phone: string;
  password: string;
  acceptTerms: boolean;
}

export interface UpdateProfileRequest {
  firstName?: string | null;
  lastName?: string | null;
  /** Chaîne vide : retire l'e-mail. */
  email?: string | null;
}

export interface AuthMessage {
  message: string;
}
