import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DOCUMENT, DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, interval, of, startWith, switchMap } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideChevronRight, lucideCircleCheck, lucideCircleX, lucideClock, lucideFlag, lucideMessageSquareReply, lucideSearch
} from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { ClientSegment, ClientSegmentResult, ClientSegmentService } from '../../services/client-segment.service';
import { AuditOrder, AuditOrderDetails, AuditOrderEvent, AuditService } from '../../services/audit.service';
import { Dispute, disputeReasonLabel } from '../../services/order.service';

/** New disputes from clients show up without a reload. */
const DISPUTES_REFRESH_MS = 30_000;

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
  providers: [provideIcons({
    lucideChevronRight, lucideCircleCheck, lucideCircleX, lucideClock, lucideFlag, lucideMessageSquareReply, lucideSearch
  })],
})
export class ClientsComponent {
  private readonly auditService = inject(AuditService);
  protected readonly segments: ClientSegment[] = ['Premier', 'Active', 'Core', 'Dormant'];
  protected readonly statuses: string[] = ['SUBMITTED', 'WORKING', 'ACCEPTED', 'FILLED', 'REJECTED', 'FAILED', 'CANCELED'];

  protected readonly clients = signal<ClientSegmentResult[]>([]);
  protected readonly filter = signal<ClientSegment | null>(null);
  protected readonly query = signal('');
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly selectedClient = signal<ClientSegmentResult | null>(null);
  protected readonly clientOrders = signal<AuditOrder[]>([]);
  protected readonly selectedOrder = signal<AuditOrderDetails | null>(null);
  protected readonly orderIdQuery = signal('');
  protected readonly statusFilters = signal<string[]>([]);
  protected readonly statusMenuOpen = signal(false);
  protected readonly ordersLoading = signal(false);
  protected readonly orderDetailsLoading = signal(false);
  /** The order whose lifecycle is open; clicking it again closes it. */
  protected readonly expandedOrderId = signal<number | null>(null);

  // ---- Client disputes ----
  protected readonly reasonLabel = disputeReasonLabel;
  protected readonly disputes = signal<Dispute[]>([]);
  protected readonly openDisputes = computed(() => this.disputes().filter(d => d.status === 'OPEN'));
  /** Show only the selected client's disputed orders. */
  protected readonly disputeFilter = signal(false);
  /** Draft replies, by dispute id. */
  protected readonly resolutionDrafts = signal<Record<number, string>>({});
  protected readonly resolvingId = signal<number | null>(null);
  protected readonly resolveError = signal('');
  private readonly disputedOrderIds = computed(() => new Set(this.disputes().map(d => d.orderId)));

  protected readonly visibleClientOrders = computed(() => {
    const statuses = this.statusFilters();
    const orderIdQuery = this.orderIdQuery().trim();
    const disputed = this.disputeFilter() ? this.disputedOrderIds() : null;
    return this.clientOrders().filter(order =>
      (statuses.length === 0 || statuses.includes(order.orderStatus))
      && (!orderIdQuery || `${order.orderId}`.includes(orderIdQuery))
      && (!disputed || disputed.has(order.orderId)));
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
    const document = inject(DOCUMENT);

    // Every dispute, refreshed so a client's new one appears while Ops has the page open
    interval(DISPUTES_REFRESH_MS).pipe(
      startWith(0),
      switchMap(() => auditService.getDisputes().pipe(catchError(() => of(null)))),
      takeUntilDestroyed(inject(DestroyRef))
    ).subscribe(disputes => disputes && this.disputes.set(disputes));
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

    this.loadOrders = (client: ClientSegmentResult, focusOrderId?: number) => {
      this.selectedClient.set(client);
      this.selectedOrder.set(null);
      this.expandedOrderId.set(null);
      this.orderIdQuery.set('');
      this.statusFilters.set([]);
      this.disputeFilter.set(false);
      this.ordersLoading.set(true);
      auditService.getOrders(client.clientId).subscribe({
        next: orders => {
          this.clientOrders.set(orders);
          this.ordersLoading.set(false);
          // Opened from a dispute: open that order's lifecycle and bring it into view
          const focus = orders.find(o => o.orderId === focusOrderId);
          if (focus) {
            this.loadOrderDetails(focus);
            setTimeout(() => document.getElementById(`ops-order-${focus.orderId}`)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }));
          }
        },
        error: () => {
          this.clientOrders.set([]);
          this.ordersLoading.set(false);
          this.error.set('Could not load this client\'s trades.');
        }
      });
    };

    this.loadOrderDetails = (order: AuditOrder) => {
      // Clicking the open order again closes it
      if (this.expandedOrderId() === order.orderId) {
        this.expandedOrderId.set(null);
        this.selectedOrder.set(null);
        return;
      }
      this.expandedOrderId.set(order.orderId);
      this.selectedOrder.set(null);
      this.resolveError.set('');
      this.orderDetailsLoading.set(true);
      auditService.getOrderDetails(order.orderId).subscribe({
        next: details => {
          // Ignore a slow answer for an order that has since been closed or swapped
          if (this.expandedOrderId() === order.orderId) {
            this.selectedOrder.set(details);
          }
          this.orderDetailsLoading.set(false);
        },
        error: () => {
          this.orderDetailsLoading.set(false);
          this.error.set('Could not load the selected trade lifecycle.');
        }
      });
    };
  }

  protected loadOrders = (_client: ClientSegmentResult, _focusOrderId?: number): void => undefined;
  protected loadOrderDetails = (_order: AuditOrder): void => undefined;

  /** "Buy", or "Buy limit", "Stop-loss" and so on for limit and stop orders. */
  protected orderLabel(order: AuditOrder): string {
    const side = order.orderType === 'BUY' ? 'Buy' : 'Sell';
    switch (order.orderKind) {
      case 'LIMIT': return `${side} limit`;
      case 'STOP': return order.orderType === 'SELL' ? 'Stop-loss' : 'Buy stop';
      default: return side;
    }
  }

  protected statusMeta(status: string): StatusMeta {
    const statusMeta: Record<string, StatusMeta> = {
      ACCEPTED: { label: 'Accepted', variant: 'secondary', icon: 'lucideCircleCheck' },
      FILLED: { label: 'Filled', variant: 'default', icon: 'lucideCircleCheck' },
      SUBMITTED: { label: 'Submitted', variant: 'secondary', icon: 'lucideClock' },
      REJECTED: { label: 'Rejected', variant: 'destructive', icon: 'lucideCircleX' },
      FAILED: { label: 'Failed', variant: 'outline', icon: 'lucideCircleX' },
      WORKING: { label: 'Working', variant: 'secondary', icon: 'lucideClock' },
      CANCELED: { label: 'Canceled', variant: 'outline', icon: 'lucideCircleX' }
    };
    return statusMeta[status] ?? { label: status, variant: 'outline', icon: 'lucideClock' };
  }

  protected effectivePrice(order: AuditOrder): number {
    return order.executionPrice ?? order.triggerPrice ?? order.price;
  }

  protected countForStatus(status: string): number {
    return this.clientOrders().filter(order => order.orderStatus === status).length;
  }

  protected isStatusSelected(status: string): boolean {
    return this.statusFilters().includes(status);
  }

  protected toggleStatus(status: string): void {
    const current = this.statusFilters();
    this.statusFilters.set(
      current.includes(status)
        ? current.filter(value => value !== status)
        : [...current, status]
    );
  }

  protected clearStatuses(): void {
    this.statusFilters.set([]);
  }

  /** Only the orders this client has disputed. Clicking again shows all. */
  protected toggleDisputes(): void {
    this.disputeFilter.update(on => !on);
    this.statusMenuOpen.set(false);
  }

  protected isDisputesSelected(): boolean {
    return this.disputeFilter();
  }

  protected openDisputeCount(orderId: number): number {
    return this.openDisputes().filter(d => d.orderId === orderId).length;
  }

  /** From the dispute queue: open the client and the disputed order. */
  protected reviewDispute(dispute: Dispute): void {
    const client = this.clients().find(c => c.clientId === dispute.clientId);
    if (client) {
      this.query.set('');
      this.loadOrders(client, dispute.orderId);
    }
  }

  protected setResolutionDraft(disputeId: number, text: string): void {
    this.resolutionDrafts.update(drafts => ({ ...drafts, [disputeId]: text }));
  }

  protected resolveDispute(dispute: Dispute): void {
    const resolution = (this.resolutionDrafts()[dispute.disputeId] ?? '').trim();
    if (!resolution) {
      this.resolveError.set('Write a reply for the client before resolving.');
      return;
    }
    this.resolvingId.set(dispute.disputeId);
    this.resolveError.set('');
    this.auditService.resolveDispute(dispute.disputeId, resolution).subscribe({
      next: resolved => {
        this.resolvingId.set(null);
        this.disputes.update(list => list.map(d => d.disputeId === resolved.disputeId ? resolved : d));
        this.selectedOrder.update(details => details && {
          ...details,
          disputes: (details.disputes ?? []).map(d => d.disputeId === resolved.disputeId ? resolved : d)
        });
      },
      error: (error: HttpErrorResponse) => {
        this.resolvingId.set(null);
        this.resolveError.set(error.status === 400 && error.error?.message ? error.error.message : 'Could not resolve the dispute.');
      }
    });
  }

  /** Theme colour for a status: gains green, problems red, everything else the normal text colour. */
  protected statusColor(status: string): string | null {
    if (status === 'FILLED') {
      return 'var(--profit)';
    }
    return status === 'REJECTED' || status === 'FAILED' ? 'var(--loss)' : null;
  }

  protected eventLabel(event: AuditOrderEvent): string {
    return this.statusMeta(event.toStatus).label;
  }

  /** Where a price came from, in words, for the audit trail. */
  protected quoteSourceLabel(source?: string | null): string {
    switch (source) {
      case 'YAHOO_LIVE': return 'live Yahoo Finance quote';
      case 'YAHOO_HISTORY': return 'Yahoo Finance historical price';
      case 'SIMULATED': return 'simulated quote (not market data)';
      default: return 'quote';
    }
  }

  protected isLastEvent(index: number, events: AuditOrderEvent[]): boolean {
    return index === events.length - 1;
  }
}
