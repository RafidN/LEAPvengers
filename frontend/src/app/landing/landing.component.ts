import { Component, computed, signal } from '@angular/core';

@Component({
  selector: 'app-landing',
  standalone: true,
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.css',
})
export class LandingComponent {
  private readonly currencyFormatter = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  });

  protected readonly balanceHidden = signal(true);
  protected readonly balance = signal(128450.32);
  protected readonly todaysPL = signal(864.17);
  protected readonly availableCash = signal(25340.11);
  protected readonly addCashPressed = signal(false);

  protected readonly balanceDisplay = computed(() => {
    if (this.balanceHidden()) {
      return '••••••';
    }

    return this.formatCurrency(this.balance());
  });

  protected readonly todaysPLDisplay = computed(() => {
    const value = this.todaysPL();
    const absValue = Math.abs(value);
    const sign = value >= 0 ? '+' : '-';
    return `${sign}${this.formatCurrency(absValue)}`;
  });

  protected readonly availableCashDisplay = computed(() =>
    this.formatCurrency(this.availableCash()),
  );

  protected readonly isProfit = computed(() => this.todaysPL() >= 0);

  protected toggleBalanceVisibility(): void {
    this.balanceHidden.update((value) => !value);
  }

  protected onAddCash(): void {
    this.addCashPressed.set(true);
    setTimeout(() => {
      this.addCashPressed.set(false);
    }, 180);
  }

  private formatCurrency(value: number): string {
    return this.currencyFormatter.format(value);
  }
}
