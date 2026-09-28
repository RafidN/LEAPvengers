import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { ClientSegment, ClientSegmentResult, ClientSegmentService } from '../../services/client-segment.service';

/** Analyst view: every client with the segment the backend calculates from portfolio value and recent orders. */
@Component({
  selector: 'app-clients',
  templateUrl: './clients.component.html',
  imports: [CurrencyPipe, HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmTableImports]
})
export class ClientsComponent {
  protected readonly segments: ClientSegment[] = ['Premier', 'Active', 'Core', 'Dormant'];

  protected readonly clients = signal<ClientSegmentResult[]>([]);
  protected readonly filter = signal<ClientSegment | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  protected readonly visibleClients = computed(() => {
    const filter = this.filter();
    return filter ? this.clients().filter(c => c.segment === filter) : this.clients();
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
