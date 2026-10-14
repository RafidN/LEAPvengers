import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideChevronRight, lucideCircleCheck, lucideCircleX, lucideClock, lucideSearch } from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { ClientSegment, ClientSegmentResult, ClientSegmentService } from '../../services/client-segment.service';
import { AuditOrder, AuditOrderDetails, AuditOrderEvent, AuditService } from '../../services/audit.service';

type StatusMeta = {
  label: string;
  variant: 'default' | 'secondary' | 'destructive' | 'outline';
  icon: string;
};

/** Ops view: look up any client, with the segment the backend calculates from portfolio value and recent orders. */
@Component({
  selector: 'app-clients',
  templateUrl: './clients.component.html',
  imports: [
    CurrencyPipe, DatePipe, DecimalPipe, FormsModule, NgIcon,
    HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmInputImports, HlmTableImports
  ],
  providers: [provideIcons({ lucideChevronRight, lucideCircleCheck, lucideCircleX, lucideClock, lucideSearch })]
})
export class ClientsComponent {
  protected readonly segments: ClientSegment[] = ['Premier', 'Active', 'Core', 'Dormant'];
  protected readonly statuses: string[] = ['SUBMITTED', 'ACCEPTED', 'FILLED', 'REJECTED', 'FAILED'];

  protected readonly clients = signal<ClientSegmentResult[]>([]);
  protected readonly filter = signal<ClientSegment | null>(null);
  protected readonly query = signal('');
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly selectedClient = signal<ClientSegmentResult | null>(null);
  protected readonly clientOrders = signal<AuditOrder[]>([]);
  protected readonly selectedOrder = signal<AuditOrderDetails | null>(null);
  protected readonly statusFilter = signal<string | null>(null);
  protected readonly ordersLoading = signal(false);
  protected readonly orderDetailsLoading = signal(false);

  protected readonly visibleClientOrders = computed(() => {
    const status = this.statusFilter();
    return status ? this.clientOrders().filter(order => order.orderStatus === status) : this.clientOrders();
  });

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
    const clientSegmentService = inject(ClientSegmentService);
    const auditService = inject(AuditService);
    clientSegmentService.getClientSegments().subscribe({
      next: clients => {
        this.clients.set(clients);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load client segments.');
        this.loading.set(false);
      }
    });

    this.loadOrders = (client: ClientSegmentResult) => {
      this.selectedClient.set(client);
      this.selectedOrder.set(null);
      this.statusFilter.set(null);
      this.ordersLoading.set(true);
      auditService.getOrders(client.clientId).subscribe({
        next: orders => {
          this.clientOrders.set(orders);
          this.ordersLoading.set(false);
        },
        error: () => {
          this.clientOrders.set([]);
          this.ordersLoading.set(false);
          this.error.set('Could not load this client\'s trades.');
        }
      });
    };

    this.loadOrderDetails = (order: AuditOrder) => {
      this.orderDetailsLoading.set(true);
      auditService.getOrderDetails(order.orderId).subscribe({
        next: details => {
          this.selectedOrder.set(details);
          this.orderDetailsLoading.set(false);
        },
        error: () => {
          this.orderDetailsLoading.set(false);
          this.error.set('Could not load the selected trade lifecycle.');
        }
      });
    };
  }

  protected loadOrders = (_client: ClientSegmentResult): void => undefined;
  protected loadOrderDetails = (_order: AuditOrder): void => undefined;

  protected statusMeta(status: string): StatusMeta {
    const statusMeta: Record<string, StatusMeta> = {
      ACCEPTED: { label: 'Accepted', variant: 'secondary', icon: 'lucideCircleCheck' },
      FILLED: { label: 'Filled', variant: 'default', icon: 'lucideCircleCheck' },
      SUBMITTED: { label: 'Submitted', variant: 'secondary', icon: 'lucideClock' },
      REJECTED: { label: 'Rejected', variant: 'destructive', icon: 'lucideCircleX' },
      FAILED: { label: 'Failed', variant: 'outline', icon: 'lucideCircleX' }
    };
    return statusMeta[status] ?? { label: status, variant: 'outline', icon: 'lucideClock' };
  }

  protected effectivePrice(order: AuditOrder): number {
    return order.executionPrice ?? order.price;
  }

  protected countForStatus(status: string): number {
    return this.clientOrders().filter(order => order.orderStatus === status).length;
  }

  protected isLastEvent(index: number, events: AuditOrderEvent[]): boolean {
    return index === events.length - 1;
  }
}
