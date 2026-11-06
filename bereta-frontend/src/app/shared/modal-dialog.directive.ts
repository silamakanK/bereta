import { AfterViewInit, Directive, ElementRef, HostListener, OnDestroy, inject, output } from '@angular/core';

/**
 * Fenêtre modale native : `<dialog appModal (closed)="…">`.
 * Ouverte avec showModal() (fond ::backdrop, focus piégé, accessible au clavier). Échap (événement
 * natif « cancel ») ou un clic sur le fond émettent `closed` : c'est le parent qui retire la fenêtre.
 */
@Directive({ selector: 'dialog[appModal]', standalone: true })
export class ModalDialogDirective implements AfterViewInit, OnDestroy {
  readonly closed = output<void>();
  private readonly dialog = inject<ElementRef<HTMLDialogElement>>(ElementRef).nativeElement;

  ngAfterViewInit(): void {
    if (this.dialog.open) return;
    if (typeof this.dialog.showModal === 'function') this.dialog.showModal();
    else this.dialog.setAttribute('open', ''); // environnements sans <dialog> (tests)
  }

  ngOnDestroy(): void {
    if (this.dialog.open && typeof this.dialog.close === 'function') this.dialog.close();
  }

  /** Échap : on laisse le parent fermer (et nettoyer son état) au lieu de la fermeture native. */
  @HostListener('cancel', ['$event'])
  protected onCancel(event: Event): void {
    event.preventDefault();
    this.closed.emit();
  }

  /** Clic sur le fond : la cible est le <dialog> lui-même et le point est hors de sa boîte. */
  @HostListener('click', ['$event'])
  protected onClick(event: MouseEvent): void {
    if (event.target !== this.dialog) return;
    const box = this.dialog.getBoundingClientRect();
    const inside = event.clientX >= box.left && event.clientX <= box.right
      && event.clientY >= box.top && event.clientY <= box.bottom;
    if (!inside) this.closed.emit();
  }
}
