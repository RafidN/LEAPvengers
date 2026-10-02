import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideSearch } from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { ClientSegment, ClientSegmentResult, ClientSegmentService } from '../../services/client-segment.service';

/** Ops view: look up any client, with the segment the backend calculates from portfolio value and recent orders. */
@Component({
  selector: 'app-clients',
  templateUrl: './clients.component.html',
  imports: [
    CurrencyPipe, FormsModule, NgIcon,
    HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmInputImports, HlmTableImports
  ],
  providers: [provideIcons({ lucideSearch })]
})
export class ClientsComponent {
  protected readonly segments: ClientSegment[] = ['Premier', 'Active', 'Core', 'Dormant'];

  protected readonly clients = signal<ClientSegmentResult[]>([]);
  protected readonly filter = signal<ClientSegment | null>(null);
  protected readonly query = signal('');
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  protected readonly visibleClients = computed(() => {
    const filter = this.filter();
    const query = this.query().trim().toLowerCase();
    return this.clients().filter(c =>
      (!filter || c.segment === filter)
      && (!query || `${c.firstName} ${c.lastName} ${c.email}`.toLowerCase().includes(query)));
  });

  protected countFor(segment: ClientSegment): number {
    return this.clients().filter(c => c.segment === segment).length;
  }

  constructor() {
    inject(ClientSegmentService).getClientSegments().subscribe({
      next: clients => {
        this.clients.set(clients);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load client segments. Is the backend running the latest code?');
        this.loading.set(false);
      }
    });
  }
}
