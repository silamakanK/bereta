import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AssociationService } from './association.service';

export const associationInterceptor: HttpInterceptorFn = (request, next) => {
  const associationId = inject(AssociationService).activeAssociationId();
  if (!associationId || !request.url.startsWith('/api/') || request.url.startsWith('/api/auth/')
      || request.url.startsWith('/api/admin/')) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { 'X-Association-Id': associationId } }));
};
