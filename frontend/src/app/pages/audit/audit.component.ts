import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowDownToLine, lucideArrowUpFromLine, lucideChevronRight, lucideCircleCheck,
  lucideCircleX, lucideClock3, lucideRefreshCw, lucideSearch
} from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { AuditClient, AuditOrder, AuditOrderDetails, AuditOrderEvent, AuditService } from '../../services/audit.service';

const STATUS_META: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline'; icon: string }> = {
  ACCEPTED: { label: 'Accepted', variant: 'secondary', icon: 'lucideCircleCheck' },
  FILLED: { label: 'Filled', variant: 'default', icon: 'lucideCircleCheck' },
  SUBMITTED: { label: 'Submitted', variant: 'secondary', icon: 'lucideClock3' },
  REJECTED: { label: 'Rejected', variant: 'destructive', icon: 'lucideCircleX' },
  FAILED: { label: 'Failed', variant: 'outline', icon: 'lucideCircleX' },
  WORKING: { label: 'Working', variant: 'secondary', icon: 'lucideClock3' },
  CANCELED: { label: 'Canceled', variant: 'outline', icon: 'lucideCircleX' }
};

@Component({
  selector: 'app-audit',
  templateUrl: './audit.component.html',
  imports: [
    CurrencyPipe, DatePipe, DecimalPipe, FormsModule, NgIcon,
    HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmInputImports, HlmTableImports
  ],
  providers: [provideIcons({
    lucideArrowDownToLine, lucideArrowUpFromLine, lucideChevronRight, lucideCircleCheck,
    lucideCircleX, lucideClock3, lucideRefreshCw, lucideSearch
  })]
})
export class AuditComponent {
  private readonly audit = inject(AuditService);

  protected readonly query = signal('');
  protected readonly clients = signal<AuditClient[]>([]);
  protected readonly selectedClient = signal<AuditClient | null>(null);
  protected readonly orders = signal<AuditOrder[]>([]);
  protected readonly selectedDetails = signal<AuditOrderDetails | null>(null);
  protected readonly loadingClients = signal(true);
  protected readonly loadingOrders = signal(false);
  protected readonly loadingDetails = signal(false);
  protected readonly error = signal('');
  protected readonly searchPerformed = signal(false);

  protected readonly selectedOrderId = computed(() => this.selectedDetails()?.order.orderId ?? null);

  constructor() {
    this.searchClients();
  }

  protected searchClients(): void {
    this.loadingClients.set(true);
    this.searchPerformed.set(true);
    this.audit.searchClients(this.query()).subscribe({
      next: clients => {
        this.clients.set(clients);
        this.loadingClients.set(false);
        this.error.set('');
      },
      error: () => {
        this.loadingClients.set(false);
        this.error.set('Could not load the client directory.');
      }
    });
  }

  protected selectClient(client: AuditClient): void {
    this.selectedClient.set(client);
    this.selectedDetails.set(null);
    this.loadingOrders.set(true);
    this.audit.getOrders(client.clientId).subscribe({
      next: orders => {
        this.orders.set(orders);
        this.loadingOrders.set(false);
        this.error.set('');
      },
      error: () => {
        this.orders.set([]);
        this.loadingOrders.set(false);
        this.error.set('Could not load this client\'s trades.');
      }
    });
  }

  protected selectOrder(order: AuditOrder): void {
    this.loadingDetails.set(true);
    this.selectedDetails.set(null);
    this.audit.getOrderDetails(order.orderId).subscribe({
      next: details => {
        this.selectedDetails.set(details);
        this.loadingDetails.set(false);
      },
      error: () => {
        this.loadingDetails.set(false);
        this.error.set('Could not load the selected order lifecycle.');
      }
    });
  }

  protected statusMeta(status: string): typeof STATUS_META[string] {
    return STATUS_META[status] ?? { label: status, variant: 'outline', icon: 'lucideClock3' };
  }

  protected effectivePrice(order: AuditOrder): number {
    return order.executionPrice ?? order.price;
  }

  protected eventLabel(event: AuditOrderEvent): string {
    return event.toStatus === 'ACCEPTED' && !event.fromStatus ? 'Trading checks passed' : this.statusMeta(event.toStatus).label;
  }

  protected isLastEvent(index: number, events: AuditOrderEvent[]): boolean {
    return index === events.length - 1;
  }
}