import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Mot de passe oublié : désactivé pour l'instant (pas d'envoi d'e-mail ni de SMS). Le responsable
 * réinitialise le mot de passe d'un trésorier ; l'administrateur celui de tout compte.
 */
@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [RouterLink],
  styleUrl: './login.component.scss',
  template: `
    <main class="login-page">
      <section class="login-card" aria-labelledby="forgot-title">
        <a class="login-brand" routerLink="/" aria-label="BERETA, accueil"><span class="login-mark">B</span><span>bereta<span class="brand-dot">.</span></span></a>
        <h1 id="forgot-title">Mot de passe oublié</h1>
        <p class="login-intro">Contactez votre responsable d’association ou l’administrateur BERETA : il vous donnera un mot de passe provisoire.</p>
        <a class="login-secondary" href="mailto:aide@bereta.fr">Contacter l’assistance</a>
        <a class="login-submit" routerLink="/connexion">Retour à la connexion</a>
      </section>
    </main>
  `,
})
export class ForgotPasswordComponent {}
