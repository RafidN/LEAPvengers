import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideShieldAlert } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { AccountHolding, AccountResponse, AccountService } from '../../services/account.service';
import { FreeMoneyService } from '../../services/free-money.service';

interface Bullet {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
}

interface Shot {
  x: number;
  y: number;
  vy: number;
  radius: number;
}

interface CollisionParticle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  lifeMs: number;
}

interface BossState {
  x: number;
  y: number;
  radius: number;
  health: number;
  maxHealth: number;
  alive: boolean;
}

@Component({
  selector: 'app-free-money',
  templateUrl: './free-money.component.html',
  imports: [FormsModule, CurrencyPipe, DecimalPipe, NgIcon, HlmButtonImports, HlmCardImports],
  providers: [provideIcons({ lucideShieldAlert })]
})
export class FreeMoneyComponent implements AfterViewInit, OnDestroy {
  private static readonly HIT_PENALTY_COOLDOWN_MS = 250;
  private static readonly RESPAWN_DELAY_MS = 2000;
  private static readonly FOCUS_SPEED_MULTIPLIER = 0.45;
  private static readonly BOSS_MAX_HEALTH = 100;

  private readonly accountService = inject(AccountService);
  private readonly freeMoneyService = inject(FreeMoneyService);

  @ViewChild('gameCanvas', { static: true })
  private readonly canvasRef!: ElementRef<HTMLCanvasElement>;

  protected readonly running = signal(false);
  protected readonly score = signal(0);
  protected readonly hits = signal(0);
  protected readonly bombsLeft = signal(3);
  protected readonly accounts = signal<AccountResponse[]>([]);
  protected readonly holdings = signal<AccountHolding[]>([]);
  protected readonly selectedAccountId = signal<number | null>(null);
  protected readonly selectedTicker = signal('');
  protected readonly holdingAfterHit = signal<number | null>(null);
  protected readonly startingHolding = signal<number | null>(null);
  protected readonly totalMoneyLost = signal(0);
  protected readonly penaltyStatus = signal('');
  protected readonly bossHealth = signal(FreeMoneyComponent.BOSS_MAX_HEALTH);
  protected readonly bossMaxHealth = signal(FreeMoneyComponent.BOSS_MAX_HEALTH);
  protected readonly bossDefeated = signal(false);
  protected readonly gameOver = signal(false);

  private readonly width = 720;
  private readonly height = 1080;

  private animationFrameId: number | null = null;
  private lastFrameTs = 0;
  private lastPatternTs = 0;
  private lastAimedPatternTs = 0;
  private lastShotTs = 0;
  private lastPenaltyRequestTs = 0;
  private bossPatternAngle = 0;
  private penaltyRequestInFlight = false;
  private bossRewardInFlight = false;
  private bossRewardClaimed = false;
  private activeBattleId: string | null = null;
  private battleResolved = false;
  private forfeitInFlight = false;
  private readonly keys = new Set<string>();

  private readonly player = {
    x: this.width / 2,
    y: this.height - 44,
    radius: 9,
    hitboxRadius: 3.2,
    speed: 340,
    active: true,
    respawnAtTs: 0,
    invulnerableUntilTs: 0
  };

  private readonly boss: BossState = {
    x: this.width / 2,
    y: 96,
    radius: 34,
    health: FreeMoneyComponent.BOSS_MAX_HEALTH,
    maxHealth: FreeMoneyComponent.BOSS_MAX_HEALTH,
    alive: true
  };

  private bullets: Bullet[] = [];
  private shots: Shot[] = [];
  private collisionParticles: CollisionParticle[] = [];
  private hitLossOverlay = {
    active: false,
    amount: null as number | null,
    x: 0,
    y: 0
  };

  constructor() {
    this.accountService.getAccounts().subscribe({
      next: accounts => {
        this.accounts.set(accounts);
        if (accounts.length > 0) {
          this.onAccountChange(accounts[0].accountId);
        }
      },
      error: () => this.penaltyStatus.set('Could not load accounts for penalty routing.')
    });
  }

  ngAfterViewInit(): void {
    this.draw();
  }

  ngOnDestroy(): void {
    this.forfeitIfNeeded();
    this.stop();
  }

  @HostListener('window:beforeunload')
  protected onBeforeUnload(): void {
    this.forfeitIfNeeded();
  }

  protected start(): void {
    if (this.activeBattleId && !this.battleResolved && !this.bossDefeated()) {
      this.penaltyStatus.set('Current battle is unresolved. Leave the battle to accept forfeit before starting again.');
      return;
    }

    if (!this.selectedAccountId()) {
      this.penaltyStatus.set('Select an account before starting.');
      return;
    }
    if (!this.selectedTicker()) {
      this.penaltyStatus.set('Select a holding ticker before starting.');
      return;
    }

    const accountId = this.selectedAccountId();
    const ticker = this.selectedTicker();
    if (!accountId || !ticker) {
      this.penaltyStatus.set('Select account and ticker before starting.');
      return;
    }

    this.resetState();
    this.penaltyStatus.set('');
    this.freeMoneyService.startBattle({ accountId, ticker }).subscribe({
      next: result => {
        this.activeBattleId = result.battleId;
        this.battleResolved = false;
        this.startingHolding.set(result.startingQuantity);
        this.running.set(true);
        this.lastFrameTs = performance.now();
        this.lastPatternTs = this.lastFrameTs;
        this.lastAimedPatternTs = this.lastFrameTs;
        this.lastShotTs = this.lastFrameTs;
        this.animationFrameId = requestAnimationFrame(ts => this.tick(ts));
      },
      error: response => {
        this.penaltyStatus.set(typeof response?.error === 'string' ? response.error : 'Could not start battle.');
      }
    });
  }

  private stop(): void {
    this.running.set(false);
    this.keys.clear();
    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }
  }

  protected onKeyDown(event: KeyboardEvent): void {
    const key = event.key.toLowerCase();
    if (!this.running()) {
      return;
    }

    if (['arrowup', 'arrowdown', 'arrowleft', 'arrowright', 'z', 'x', 'shift'].includes(key)) {
      event.preventDefault();
    }

    if (key === 'x' && !event.repeat) {
      this.useBomb();
      return;
    }

    this.keys.add(key);
  }

  protected onKeyUp(event: KeyboardEvent): void {
    this.keys.delete(event.key.toLowerCase());
  }

  protected onAccountChange(accountId: number): void {
    this.selectedAccountId.set(accountId);
    this.selectedTicker.set('');
    this.refreshHoldings();
  }

  protected onTickerChange(ticker: string): void {
    this.selectedTicker.set(ticker);
  }

  private tick(timestamp: number): void {
    if (!this.running()) {
      return;
    }

    const deltaSec = Math.min((timestamp - this.lastFrameTs) / 1000, 0.035);
    this.lastFrameTs = timestamp;

    this.updatePlayer(deltaSec, timestamp);
    this.updateBoss(timestamp);
    this.tryFire(timestamp);
    this.spawnBossPattern(timestamp);
    this.updateBullets(deltaSec);
    this.updateShots(deltaSec);
    this.updateCollisionParticles(deltaSec);
    this.resolveCollisions(timestamp);
    this.draw();

    if (this.running()) {
      this.animationFrameId = requestAnimationFrame(ts => this.tick(ts));
    }
  }

  private isFocused(): boolean {
    return this.keys.has('shift');
  }

  private updatePlayer(deltaSec: number, timestamp: number): void {
    if (!this.player.active && timestamp >= this.player.respawnAtTs) {
      this.player.active = true;
      this.player.invulnerableUntilTs = timestamp + 700;
      this.player.x = this.width / 2;
      this.player.y = this.height - 44;
      this.hitLossOverlay.active = false;
      this.hitLossOverlay.amount = null;
    }

    if (!this.player.active) {
      return;
    }

    let dx = 0;
    let dy = 0;

    if (this.keys.has('arrowleft')) {
      dx -= 1;
    }
    if (this.keys.has('arrowright')) {
      dx += 1;
    }
    if (this.keys.has('arrowup')) {
      dy -= 1;
    }
    if (this.keys.has('arrowdown')) {
      dy += 1;
    }

    if (dx !== 0 && dy !== 0) {
      const normalize = Math.sqrt(2);
      dx /= normalize;
      dy /= normalize;
    }

    const speed = this.isFocused()
      ? this.player.speed * FreeMoneyComponent.FOCUS_SPEED_MULTIPLIER
      : this.player.speed;

    this.player.x += dx * speed * deltaSec;
    this.player.y += dy * speed * deltaSec;

    this.player.x = Math.max(this.player.radius, Math.min(this.width - this.player.radius, this.player.x));
    this.player.y = Math.max(this.player.radius, Math.min(this.height - this.player.radius, this.player.y));

    if (timestamp >= this.player.invulnerableUntilTs && this.hits() >= 20) {
      this.gameOver.set(true);
      this.stop();
    }
  }

  private updateBoss(timestamp: number): void {
    if (!this.boss.alive) {
      return;
    }

    const time = timestamp / 1000;
    this.boss.x = (this.width / 2) + Math.sin(time * 0.85) * 220;
    this.boss.y = 90 + Math.sin(time * 1.3) * 18;
  }

  private tryFire(timestamp: number): void {
    if (!this.player.active) {
      return;
    }
    if (!this.keys.has('z')) {
      return;
    }
    if (timestamp - this.lastShotTs < 105) {
      return;
    }

    this.lastShotTs = timestamp;
    this.shots.push({
      x: this.player.x,
      y: this.player.y - this.player.radius - 2,
      vy: -570,
      radius: 4
    });
  }

  private spawnBossPattern(timestamp: number): void {
    if (!this.boss.alive) {
      return;
    }

    if (timestamp - this.lastPatternTs >= 420) {
      this.lastPatternTs = timestamp;
      const radialCount = 10;
      this.bossPatternAngle += 0.23;
      for (let i = 0; i < radialCount; i++) {
        const angle = this.bossPatternAngle + ((Math.PI * 2 * i) / radialCount);
        const speed = 85 + ((i % 2) * 35);
        this.bullets.push({
          x: this.boss.x,
          y: this.boss.y,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          radius: 5
        });
      }
    }

    if (timestamp - this.lastAimedPatternTs >= 1200) {
      this.lastAimedPatternTs = timestamp;
      const targetX = this.player.active ? this.player.x : (this.width / 2);
      const targetY = this.player.active ? this.player.y : (this.height - 44);
      const baseAngle = Math.atan2(targetY - this.boss.y, targetX - this.boss.x);
      const fanSpread = 0.24;
      const fanCount = 4;

      for (let i = 0; i < fanCount; i++) {
        const t = i / (fanCount - 1);
        const angle = baseAngle + ((t - 0.5) * fanSpread * 2);
        const speed = 160;
        this.bullets.push({
          x: this.boss.x,
          y: this.boss.y,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          radius: 5
        });
      }
    }
  }

  private updateBullets(deltaSec: number): void {
    this.bullets = this.bullets
      .map(b => ({ ...b, x: b.x + (b.vx * deltaSec), y: b.y + (b.vy * deltaSec) }))
      .filter(b => b.y < this.height + 20 && b.x > -20 && b.x < this.width + 20);
  }

  private updateShots(deltaSec: number): void {
    this.shots = this.shots
      .map(s => ({ ...s, y: s.y + (s.vy * deltaSec) }))
      .filter(s => s.y > -20);
  }

  private updateCollisionParticles(deltaSec: number): void {
    const decayMs = deltaSec * 1000;
    this.collisionParticles = this.collisionParticles
      .map(particle => ({
        ...particle,
        x: particle.x + (particle.vx * deltaSec),
        y: particle.y + (particle.vy * deltaSec),
        lifeMs: particle.lifeMs - decayMs
      }))
      .filter(particle => particle.lifeMs > 0);
  }

  private resolveCollisions(timestamp: number): void {
    const shotRemoved = new Set<number>();

    if (this.boss.alive) {
      for (let si = 0; si < this.shots.length; si++) {
        if (shotRemoved.has(si)) {
          continue;
        }
        const shot = this.shots[si];
        const dx = shot.x - this.boss.x;
        const dy = shot.y - this.boss.y;
        const hitRadius = shot.radius + this.boss.radius;
        if ((dx * dx) + (dy * dy) <= hitRadius * hitRadius) {
          shotRemoved.add(si);
          const nextHealth = Math.max(0, this.boss.health - 1);
          this.boss.health = nextHealth;
          this.bossHealth.set(nextHealth);
          this.score.update(value => value + 5);
          if (nextHealth <= 0) {
            this.handleBossDefeat();
          }
        }
      }
    }

    this.shots = this.shots.filter((_, index) => !shotRemoved.has(index));

    if (!this.player.active || timestamp < this.player.invulnerableUntilTs || !this.boss.alive) {
      return;
    }

    for (const bullet of this.bullets) {
      const dx = this.player.x - bullet.x;
      const dy = this.player.y - bullet.y;
      const hitRadius = this.player.hitboxRadius + bullet.radius;
      if ((dx * dx) + (dy * dy) <= hitRadius * hitRadius) {
        const clearedOnHit = this.bullets.length;
        this.bullets = [];
        if (clearedOnHit > 0) {
          this.score.update(value => value + clearedOnHit);
        }
        this.hits.update(value => value + 1);
        this.spawnCollisionBurst(this.player.x, this.player.y);
        this.hitLossOverlay.active = true;
        this.hitLossOverlay.amount = null;
        this.hitLossOverlay.x = this.player.x;
        this.hitLossOverlay.y = this.player.y;
        this.player.active = false;
        this.player.respawnAtTs = timestamp + FreeMoneyComponent.RESPAWN_DELAY_MS;
        this.player.invulnerableUntilTs = this.player.respawnAtTs;
        this.applyHitPenalty(timestamp);
        break;
      }
    }
  }

  private handleBossDefeat(): void {
    if (this.bossRewardClaimed || this.bossRewardInFlight) {
      return;
    }

    this.boss.alive = false;
    this.bossDefeated.set(true);
    this.bullets = [];
    this.stop();

    const accountId = this.selectedAccountId();
    const ticker = this.selectedTicker();
    if (!accountId || !ticker) {
      this.penaltyStatus.set('Boss defeated, but no account/ticker selected for reward.');
      return;
    }

    this.bossRewardInFlight = true;
    if (!this.activeBattleId) {
      this.penaltyStatus.set('Missing battle session for reward.');
      return;
    }

    this.freeMoneyService.registerBossDefeat({ battleId: this.activeBattleId, accountId, ticker }).subscribe({
      next: result => {
        this.bossRewardInFlight = false;
        this.bossRewardClaimed = true;
        this.battleResolved = true;
        this.holdingAfterHit.set(result.quantityAfter);
        this.penaltyStatus.set(`Boss defeated. Reward applied: ${result.ticker} doubled to ${result.quantityAfter.toFixed(4)}.`);
        this.refreshHoldings();
      },
      error: response => {
        this.bossRewardInFlight = false;
        this.penaltyStatus.set(typeof response?.error === 'string' ? response.error : 'Boss reward failed to apply.');
      }
    });
  }

  private spawnCollisionBurst(x: number, y: number): void {
    const count = 20;
    for (let i = 0; i < count; i++) {
      const angle = (Math.PI * 2 * i) / count;
      const speed = 160 + Math.random() * 170;
      this.collisionParticles.push({
        x,
        y,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        radius: 3 + Math.random() * 2,
        lifeMs: 450 + Math.random() * 250
      });
    }
  }

  private refreshHoldings(): void {
    const accountId = this.selectedAccountId();
    if (!accountId) {
      this.holdings.set([]);
      return;
    }

    this.accountService.getPortfolio(accountId).subscribe({
      next: portfolio => {
        const positive = portfolio.holdings.filter(holding => holding.quantity > 0);
        this.holdings.set(positive);

        if (positive.length === 0) {
          this.selectedTicker.set('');
          return;
        }

        const ticker = this.selectedTicker();
        const stillHeld = positive.some(holding => holding.ticker === ticker);
        if (!stillHeld) {
          this.selectedTicker.set(positive[0].ticker);
        }
      },
      error: () => this.penaltyStatus.set('Could not refresh holdings.')
    });
  }

  private applyHitPenalty(timestamp: number): void {
    if (this.penaltyRequestInFlight) {
      return;
    }
    if (timestamp - this.lastPenaltyRequestTs < FreeMoneyComponent.HIT_PENALTY_COOLDOWN_MS) {
      return;
    }

    const accountId = this.selectedAccountId();
    const ticker = this.selectedTicker();
    if (!accountId || !ticker || !this.activeBattleId) {
      return;
    }

    this.penaltyRequestInFlight = true;
    this.lastPenaltyRequestTs = timestamp;
    this.freeMoneyService.registerHit({ battleId: this.activeBattleId, accountId, ticker }).subscribe({
      next: result => {
        const price = this.holdings().find(holding => holding.ticker === result.ticker)?.latestPrice ?? 0;
        const moneyLostThisHit = result.quantityDeducted * price;
        this.totalMoneyLost.update(value => value + moneyLostThisHit);
        this.hitLossOverlay.amount = moneyLostThisHit;
        this.holdingAfterHit.set(result.quantityAfter);
        this.penaltyStatus.set(`Hit penalty: -${result.quantityDeducted.toFixed(4)} ${result.ticker} (about ${moneyLostThisHit.toFixed(2)} lost).`);
        this.refreshHoldings();
        this.penaltyRequestInFlight = false;

        if (result.quantityAfter <= 0) {
          this.penaltyStatus.set(`Holding for ${result.ticker} is now zero.`);
          this.gameOver.set(true);
          this.stop();
          this.draw();
        }
      },
      error: response => {
        this.penaltyStatus.set(typeof response?.error === 'string' ? response.error : 'Failed to apply 10% hit penalty.');
        this.penaltyRequestInFlight = false;
      }
    });
  }

  private useBomb(): void {
    if (this.bombsLeft() <= 0) {
      return;
    }
    this.bullets = [];
    this.bombsLeft.update(value => value - 1);
  }

  private draw(): void {
    const canvas = this.canvasRef.nativeElement;
    const context = canvas.getContext('2d');
    if (!context) {
      return;
    }

    context.clearRect(0, 0, this.width, this.height);

    const background = context.createLinearGradient(0, 0, this.width, this.height);
    background.addColorStop(0, '#020613');
    background.addColorStop(1, '#101f3f');
    context.fillStyle = background;
    context.fillRect(0, 0, this.width, this.height);

    context.strokeStyle = 'rgba(80, 210, 255, 0.2)';
    context.lineWidth = 1;
    for (let x = 0; x <= this.width; x += 40) {
      context.beginPath();
      context.moveTo(x, 0);
      context.lineTo(x, this.height);
      context.stroke();
    }

    context.fillStyle = '#ff4f5e';
    for (const bullet of this.bullets) {
      context.beginPath();
      context.arc(bullet.x, bullet.y, bullet.radius, 0, Math.PI * 2);
      context.fill();
    }

    if (this.boss.alive) {
      context.beginPath();
      context.arc(this.boss.x, this.boss.y, this.boss.radius + 4, 0, Math.PI * 2);
      context.fillStyle = 'rgba(255, 82, 162, 0.22)';
      context.fill();

      context.beginPath();
      context.arc(this.boss.x, this.boss.y, this.boss.radius, 0, Math.PI * 2);
      context.fillStyle = '#ff4fa9';
      context.fill();

      context.beginPath();
      context.arc(this.boss.x, this.boss.y, this.boss.radius * 0.45, 0, Math.PI * 2);
      context.fillStyle = '#fff0f7';
      context.fill();
    }

    context.fillStyle = '#6ee7ff';
    for (const shot of this.shots) {
      context.beginPath();
      context.arc(shot.x, shot.y, shot.radius, 0, Math.PI * 2);
      context.fill();
    }

    context.fillStyle = '#51ff6a';
    for (const particle of this.collisionParticles) {
      context.beginPath();
      context.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2);
      context.fill();
    }

    if (this.player.active) {
      context.beginPath();
      context.arc(this.player.x, this.player.y, this.player.radius + 2, 0, Math.PI * 2);
      context.strokeStyle = this.player.invulnerableUntilTs > performance.now() ? '#fff7a1' : '#ffffff';
      context.lineWidth = 2;
      context.stroke();

      context.beginPath();
      context.arc(this.player.x, this.player.y, this.player.radius, 0, Math.PI * 2);
      context.fillStyle = '#d6ff4a';
      context.fill();

      if (this.isFocused()) {
        context.beginPath();
        context.arc(this.player.x, this.player.y, this.player.hitboxRadius + 1.5, 0, Math.PI * 2);
        context.strokeStyle = '#ff0000';
        context.lineWidth = 1.5;
        context.stroke();

        context.beginPath();
        context.arc(this.player.x, this.player.y, this.player.hitboxRadius, 0, Math.PI * 2);
        context.fillStyle = '#ff0000';
        context.fill();
      }
    }

    this.drawBossHealthBar(context);
    this.drawHitLossOverlay(context);

    if (!this.running()) {
      context.fillStyle = 'rgba(0, 0, 0, 0.45)';
      context.fillRect(0, 0, this.width, this.height);
      context.fillStyle = '#ffffff';
      context.textAlign = 'center';

      if (this.gameOver()) {
        context.font = '900 52px ui-sans-serif, system-ui';
        context.fillText('GAME OVER', this.width / 2, 96);
        context.font = '700 34px ui-sans-serif, system-ui';
        context.fillText(
          `Total Money Lost: ${this.totalMoneyLost().toLocaleString('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
          })}`,
          this.width / 2,
          this.height / 2
        );
      } else {
        context.font = 'bold 28px ui-sans-serif, system-ui';
        context.fillText(this.bossDefeated() ? 'Boss Defeated' : 'Press Start to Begin', this.width / 2, this.height / 2);
      }
    }
  }

  private drawBossHealthBar(context: CanvasRenderingContext2D): void {
    const barWidth = 420;
    const barHeight = 14;
    const x = (this.width - barWidth) / 2;
    const y = 18;

    context.fillStyle = 'rgba(0, 0, 0, 0.45)';
    context.fillRect(x, y, barWidth, barHeight);

    const ratio = Math.max(0, Math.min(1, this.bossHealth() / this.bossMaxHealth()));
    context.fillStyle = '#ff4fa9';
    context.fillRect(x, y, barWidth * ratio, barHeight);

    context.strokeStyle = '#ffffff';
    context.lineWidth = 1.5;
    context.strokeRect(x, y, barWidth, barHeight);
  }

  private drawHitLossOverlay(context: CanvasRenderingContext2D): void {
    if (!this.hitLossOverlay.active) {
      return;
    }

    const text = this.hitLossOverlay.amount === null
      ? '-$...'
      : `-${this.hitLossOverlay.amount.toLocaleString('en-US', {
          style: 'currency',
          currency: 'USD',
          minimumFractionDigits: 2,
          maximumFractionDigits: 2
        })}`;

    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.font = '900 54px ui-sans-serif, system-ui';
    context.lineWidth = 6;
    context.strokeStyle = 'rgba(0, 0, 0, 0.65)';
    context.strokeText(text, this.hitLossOverlay.x, this.hitLossOverlay.y - 36);
    context.fillStyle = '#ffef62';
    context.fillText(text, this.hitLossOverlay.x, this.hitLossOverlay.y - 36);
  }

  private resetState(): void {
    this.score.set(0);
    this.hits.set(0);
    this.bombsLeft.set(3);
    this.holdingAfterHit.set(null);
    this.totalMoneyLost.set(0);
    this.bossHealth.set(FreeMoneyComponent.BOSS_MAX_HEALTH);
    this.bossMaxHealth.set(FreeMoneyComponent.BOSS_MAX_HEALTH);
    this.bossDefeated.set(false);
    this.gameOver.set(false);

    this.boss.health = FreeMoneyComponent.BOSS_MAX_HEALTH;
    this.boss.maxHealth = FreeMoneyComponent.BOSS_MAX_HEALTH;
    this.boss.alive = true;
    this.boss.x = this.width / 2;
    this.boss.y = 96;

    this.bossPatternAngle = 0;
    this.bossRewardInFlight = false;
    this.bossRewardClaimed = false;
    this.penaltyRequestInFlight = false;
    this.battleResolved = false;
    this.forfeitInFlight = false;
    this.activeBattleId = null;
    this.startingHolding.set(null);

    this.player.x = this.width / 2;
    this.player.y = this.height - 44;
    this.player.active = true;
    this.player.respawnAtTs = 0;
    this.player.invulnerableUntilTs = 0;
    this.hitLossOverlay.active = false;
    this.hitLossOverlay.amount = null;

    this.bullets = [];
    this.shots = [];
    this.collisionParticles = [];
  }

  private forfeitIfNeeded(): void {
    if (this.forfeitInFlight || this.battleResolved || this.bossDefeated()) {
      return;
    }

    const battleId = this.activeBattleId;
    const accountId = this.selectedAccountId();
    const ticker = this.selectedTicker();
    if (!battleId || !accountId || !ticker) {
      return;
    }

    this.forfeitInFlight = true;
    this.freeMoneyService.forfeitBattle({ battleId, accountId, ticker }).subscribe({
      next: result => {
        this.forfeitInFlight = false;
        this.battleResolved = true;
        this.holdingAfterHit.set(result.quantityAfter);
        this.penaltyStatus.set(result.message);
      },
      error: () => {
        this.forfeitInFlight = false;
      }
    });
  }
}
