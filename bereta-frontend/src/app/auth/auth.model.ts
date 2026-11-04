export type Role = 'RESPONSABLE' | 'TRESORIER' | 'ADMIN';

export interface AuthUser {
  id: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  role: Role;
  emailVerified: boolean;
  phoneVerified: boolean;
  mustChangePassword: boolean;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresInSeconds: number;
  user: AuthUser;
}

/** Étape 1 de la connexion : jamais de jeton, seulement un challenge SMS ou une demande de téléphone. */
export interface LoginStep {
  step: 'OTP_REQUIRED' | 'PHONE_REQUIRED';
  challengeId: string | null;
  maskedPhone: string | null;
  expiresInSeconds: number | null;
  resendAvailableInSeconds: number | null;
  enrollmentToken: string | null;
}

export interface CodeSent {
  challengeId: string;
  channel: 'SMS' | 'EMAIL' | null;
  maskedDestination: string | null;
  expiresInSeconds: number;
  resendAvailableInSeconds: number;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  phone: string;
  email: string | null;
  password: string;
  acceptTerms: boolean;
}

export interface RegistrationResponse {
  phoneVerification: CodeSent;
  emailVerificationSent: boolean;
}

export interface ForgotPasswordResponse {
  message: string;
  channel: 'SMS' | 'EMAIL';
  challengeId: string | null;
  expiresInSeconds: number;
}

export interface AuthMessage {
  message: string;
}
