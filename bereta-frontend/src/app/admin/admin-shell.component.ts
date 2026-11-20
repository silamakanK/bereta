import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/** Cadre de l'espace administrateur : bandeau permanent, navigation, contenu des écrans. */
@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './admin-shell.component.html',
  styleUrl: './admin-shell.component.scss',
})
export class AdminShellComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.currentUser;
  /** Écrans des lots suivants, annoncés mais pas encore ouverts. */
  protected readonly upcoming = ['Tableau de bord', 'Opérations'];

  protected logout(): void {
    this.auth.logout().subscribe({ complete: () => void this.router.navigateByUrl('/connexion') });
  }
}
