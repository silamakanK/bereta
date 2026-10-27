export interface AuthUser {
  email: string;
  displayName: string;
  role: 'RESPONSABLE' | 'TRESORIER';
}

export interface CsrfTokenResponse {
  token: string;
  headerName: string;
  parameterName: string;
}
