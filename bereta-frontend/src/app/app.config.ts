import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { associationInterceptor } from './associations/association.interceptor';
import { authInterceptor } from './auth/auth.interceptor';

// Les pipes date/currency utilisent 'fr-FR' : sans ces données, le tableau de bord plante (NG0701).
registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    provideHttpClient(withInterceptors([associationInterceptor, authInterceptor])),
    provideRouter(routes)
  ]
};
