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

type Difficulty = 'normal' | 'hard' | 'lunatic';

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
  private static readonly NORMAL_BOSS_MAX_HEALTH = 100;
  private static readonly DIFFICULTY_CONFIG: Record<Difficulty, { health: number, phaseCount: number }> = {
    normal: { health: FreeMoneyComponent.NORMAL_BOSS_MAX_HEALTH, phaseCount: 3 },
    hard: { health: FreeMoneyComponent.NORMAL_BOSS_MAX_HEALTH + 50, phaseCount: 4 },
    lunatic: { health: FreeMoneyComponent.NORMAL_BOSS_MAX_HEALTH + 100, phaseCount: 5 }
  };
  private static readonly PHASE_BANNER_DURATION_MS = 2200;
  private static readonly BOSS_DEFEAT_ANIMATION_MS = 1350;

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
  protected readonly selectedDifficulty = signal<Difficulty>('normal');
  protected readonly bossHealth = signal(FreeMoneyComponent.DIFFICULTY_CONFIG.normal.health);
  protected readonly bossMaxHealth = signal(FreeMoneyComponent.DIFFICULTY_CONFIG.normal.health);
  protected readonly bossDefeated = signal(false);
  protected readonly gameOver = signal(false);

  private readonly width = 720;
  private readonly height = 1080;

  private animationFrameId: number | null = null;
  private lastFrameTs = 0;
  private lastPatternTs = 0;
  private lastAimedPatternTs = 0;
  private lastSpellPatternTs = 0;
  private lastShotTs = 0;
  private lastPenaltyRequestTs = 0;
  private bossPatternAngle = 0;
  private bossPhase = 1;
  private bossPhaseCount = FreeMoneyComponent.DIFFICULTY_CONFIG.normal.phaseCount;
  private activePhaseBannerText: string | null = null;
  private activePhaseBannerUntilTs = 0;
  private bossDefeatAnimating = false;
  private bossDefeatAnimationEndTs = 0;
  private lastBossDefeatPulseTs = 0;
  private bossDefeatOrigin = { x: 0, y: 0 };
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
    radius: 13,
    hitboxRadius: 3.2,
    speed: 340,
    active: true,
    respawnAtTs: 0,
    invulnerableUntilTs: 0
  };

  private readonly boss: BossState = {
    x: this.width / 2,
    y: 96,
    radius: 52,
    health: FreeMoneyComponent.DIFFICULTY_CONFIG.normal.health,
    maxHealth: FreeMoneyComponent.DIFFICULTY_CONFIG.normal.health,
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
    this.freeMoneyService.startBattle({ accountId, ticker, difficulty: this.selectedDifficulty() }).subscribe({
      next: result => {
        this.activeBattleId = result.battleId;
        this.battleResolved = false;
        this.startingHolding.set(result.startingQuantity);
        this.running.set(true);
        this.lastFrameTs = performance.now();
        this.lastPatternTs = this.lastFrameTs;
        this.lastAimedPatternTs = this.lastFrameTs;
        this.lastSpellPatternTs = this.lastFrameTs;
        this.lastShotTs = this.lastFrameTs;
        this.showPhaseBanner(1);
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

  protected onDifficultyChange(difficulty: Difficulty): void {
    if (this.running()) {
      this.penaltyStatus.set('Finish the current run before changing difficulty.');
      return;
    }

    this.selectedDifficulty.set(difficulty);
    const config = FreeMoneyComponent.DIFFICULTY_CONFIG[difficulty];
    this.bossHealth.set(config.health);
    this.bossMaxHealth.set(config.health);
    this.penaltyStatus.set(`Difficulty set to ${difficulty}.`);
    this.draw();
  }

  protected onCanvasClick(): void {
    if (!this.running()) {
      this.start();
    }
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
    this.updateBossDefeatAnimation(timestamp);
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
    if (this.bossPhase === 1) {
      this.boss.x = (this.width / 2) + Math.sin(time * 0.85) * 220;
      this.boss.y = 90 + Math.sin(time * 1.2) * 20;
      return;
    }

    if (this.bossPhase === 2) {
      this.boss.x = (this.width / 2) + Math.sin(time * 1.25) * 190 + Math.sin(time * 2.6) * 34;
      this.boss.y = 102 + Math.sin(time * 2.2) * 24;
      return;
    }

    if (this.bossPhase === 3) {
      this.boss.x = (this.width / 2) + Math.sin(time * 1.85) * 165 + Math.cos(time * 2.9) * 42;
      this.boss.y = 108 + Math.sin(time * 3.2) * 30;
      return;
    }

    if (this.bossPhase === 4) {
      this.boss.x = (this.width / 2) + Math.sin(time * 2.5) * 138 + Math.cos(time * 4.1) * 46;
      this.boss.y = 116 + Math.sin(time * 4.2) * 34;
      return;
    }

    this.boss.x = (this.width / 2) + Math.sin(time * 3.1) * 120 + Math.cos(time * 5.4) * 54;
    this.boss.y = 122 + Math.sin(time * 5.8) * 38;
  }

  private tryFire(timestamp: number): void {
    if (this.bossDefeatAnimating) {
      return;
    }
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
      radius: 6
    });
  }

  private spawnBossPattern(timestamp: number): void {
    if (!this.boss.alive) {
      return;
    }

    if (this.bossPhase === 1) {
      if (timestamp - this.lastPatternTs >= 460) {
        this.lastPatternTs = timestamp;
        const radialCount = 12;
        this.bossPatternAngle += 0.2;
        for (let i = 0; i < radialCount; i++) {
          const angle = this.bossPatternAngle + ((Math.PI * 2 * i) / radialCount);
          const speed = 95 + ((i % 2) * 30);
          this.bullets.push({
            x: this.boss.x,
            y: this.boss.y,
            vx: Math.cos(angle) * speed,
            vy: Math.sin(angle) * speed,
            radius: 7
          });
        }
      }

      if (timestamp - this.lastAimedPatternTs >= 1100) {
        this.lastAimedPatternTs = timestamp;
        const targetX = this.player.active ? this.player.x : (this.width / 2);
        const targetY = this.player.active ? this.player.y : (this.height - 44);
        const baseAngle = Math.atan2(targetY - this.boss.y, targetX - this.boss.x);
        const fanSpread = 0.3;
        const fanCount = 5;

        for (let i = 0; i < fanCount; i++) {
          const t = i / (fanCount - 1);
          const angle = baseAngle + ((t - 0.5) * fanSpread * 2);
          const speed = 180;
          this.bullets.push({
            x: this.boss.x,
            y: this.boss.y,
            vx: Math.cos(angle) * speed,
            vy: Math.sin(angle) * speed,
            radius: 7
          });
        }
      }
      return;
    }

    if (this.bossPhase === 2) {
      if (timestamp - this.lastPatternTs >= 300) {
        this.lastPatternTs = timestamp;
        this.bossPatternAngle += 0.33;

        for (const direction of [-1, 1]) {
          const emitterX = this.boss.x + (direction * 26);
          const emitterY = this.boss.y + 8;
          for (let step = 0; step < 3; step++) {
            const angle = this.bossPatternAngle + (direction * (0.55 + (step * 0.18)));
            const speed = 145 + (step * 22);
            this.bullets.push({
              x: emitterX,
              y: emitterY,
              vx: Math.cos(angle) * speed,
              vy: Math.sin(angle) * speed,
              radius: 7
            });
          }
        }
      }

      if (timestamp - this.lastAimedPatternTs >= 760) {
        this.lastAimedPatternTs = timestamp;
        const targetX = this.player.active ? this.player.x : (this.width / 2);
        const targetY = this.player.active ? this.player.y : (this.height - 44);
        const baseAngle = Math.atan2(targetY - this.boss.y, targetX - this.boss.x);
        const arcCount = 9;
        const arcSpread = 0.68;

        for (let i = 0; i < arcCount; i++) {
          const t = i / (arcCount - 1);
          const angle = baseAngle + ((t - 0.5) * arcSpread * 2);
          this.bullets.push({
            x: this.boss.x,
            y: this.boss.y,
            vx: Math.cos(angle) * 195,
            vy: Math.sin(angle) * 195,
            radius: 7
          });
        }
      }
      return;
    }

    if (this.bossPhase === 3) {
      if (timestamp - this.lastPatternTs >= 230) {
        this.lastPatternTs = timestamp;
        this.bossPatternAngle += 0.37;
        const ringCount = 18;
        for (let i = 0; i < ringCount; i++) {
          const angle = this.bossPatternAngle + ((Math.PI * 2 * i) / ringCount);
          const speed = (i % 2 === 0) ? 165 : 225;
          this.bullets.push({
            x: this.boss.x,
            y: this.boss.y,
            vx: Math.cos(angle) * speed,
            vy: Math.sin(angle) * speed,
            radius: 7
          });
        }
      }

      if (timestamp - this.lastSpellPatternTs >= 920) {
        this.lastSpellPatternTs = timestamp;
        const curtainCount = 11;
        const pulse = Math.sin(timestamp / 360) * 0.18;
        for (let i = 0; i < curtainCount; i++) {
          const t = i / (curtainCount - 1);
          const angle = (Math.PI / 2) + ((t - 0.5) * 1.35) + pulse;
          const speed = 210 + (Math.abs(t - 0.5) * 45);
          this.bullets.push({
            x: this.boss.x,
            y: this.boss.y,
            vx: Math.cos(angle) * speed,
            vy: Math.sin(angle) * speed,
            radius: 7
          });
        }
      }
      return;
    }

    if (this.bossPhase === 4) {
      if (timestamp - this.lastPatternTs >= 190) {
        this.lastPatternTs = timestamp;
        this.bossPatternAngle += 0.42;
        const ringCount = 20;
        for (let i = 0; i < ringCount; i++) {
          const angle = this.bossPatternAngle + ((Math.PI * 2 * i) / ringCount);
          const speed = 190 + ((i % 3) * 32);
          this.bullets.push({
            x: this.boss.x,
            y: this.boss.y,
            vx: Math.cos(angle) * speed,
            vy: Math.sin(angle) * speed,
            radius: 7
          });
        }
      }

      if (timestamp - this.lastSpellPatternTs >= 680) {
        this.lastSpellPatternTs = timestamp;
        const spiralPairs = 2;
        for (let pair = 0; pair < spiralPairs; pair++) {
          const spinOffset = pair * Math.PI;
          for (let s = 0; s < 6; s++) {
            const angle = this.bossPatternAngle + spinOffset + (s * 0.22);
            const speed = 205 + (s * 16);
            this.bullets.push({
              x: this.boss.x,
              y: this.boss.y,
              vx: Math.cos(angle) * speed,
              vy: Math.sin(angle) * speed,
              radius: 7
            });
          }
        }
      }
      return;
    }

    if (timestamp - this.lastPatternTs >= 155) {
      this.lastPatternTs = timestamp;
      this.bossPatternAngle += 0.52;
      const ringCount = 24;
      for (let i = 0; i < ringCount; i++) {
        const angle = this.bossPatternAngle + ((Math.PI * 2 * i) / ringCount);
        const speed = 225 + ((i % 4) * 26);
        this.bullets.push({
          x: this.boss.x,
          y: this.boss.y,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          radius: 7
        });
      }
    }

    if (timestamp - this.lastSpellPatternTs >= 540) {
      this.lastSpellPatternTs = timestamp;
      const curtainCount = 15;
      const pulse = Math.sin(timestamp / 240) * 0.24;
      for (let i = 0; i < curtainCount; i++) {
        const t = i / (curtainCount - 1);
        const angle = (Math.PI / 2) + ((t - 0.5) * 1.5) + pulse;
        const speed = 260 + (Math.abs(t - 0.5) * 70);
        this.bullets.push({
          x: this.boss.x,
          y: this.boss.y,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          radius: 7
        });
      }
    }
  }

  private getBossPhase(): number {
    const ratio = this.boss.health / this.boss.maxHealth;
    for (let phase = 1; phase <= this.bossPhaseCount; phase++) {
      const threshold = (this.bossPhaseCount - phase) / this.bossPhaseCount;
      if (ratio > threshold) {
        return phase;
      }
    }
    return this.bossPhaseCount;
  }

  private updateBossDefeatAnimation(timestamp: number): void {
    if (!this.bossDefeatAnimating) {
      return;
    }

    if (timestamp - this.lastBossDefeatPulseTs >= 140) {
      this.lastBossDefeatPulseTs = timestamp;
      this.spawnBossDefeatBurst(14, 130, 220, 8);
    }

    if (timestamp >= this.bossDefeatAnimationEndTs) {
      this.bossDefeatAnimating = false;
      this.bullets = [];
      this.finalizeBossDefeat();
    }
  }

  private spawnBossDefeatBurst(count: number, minSpeed: number, maxSpeed: number, radius: number): void {
    for (let i = 0; i < count; i++) {
      const angle = (Math.PI * 2 * i) / count;
      const jitter = (Math.random() - 0.5) * 0.28;
      const speed = minSpeed + Math.random() * (maxSpeed - minSpeed);
      this.bullets.push({
        x: this.bossDefeatOrigin.x,
        y: this.bossDefeatOrigin.y,
        vx: Math.cos(angle + jitter) * speed,
        vy: Math.sin(angle + jitter) * speed,
        radius
      });
    }
  }

  private getPhaseBannerText(phase: number): string {
    if (phase === 1) {
      return 'Spell Card - Scarlet Bloom';
    }
    if (phase === 2) {
      return 'Spell Card - Twin Spiral Barrage';
    }
    if (phase === 3) {
      return 'Spell Card - Celestial Curtain';
    }
    if (phase === 4) {
      return 'Spell Card - Starfall Cataclysm';
    }
    return 'Last Word - Void Lotus Rain';
  }

  private showPhaseBanner(phase: number): void {
    this.activePhaseBannerText = this.getPhaseBannerText(phase);
    this.activePhaseBannerUntilTs = performance.now() + FreeMoneyComponent.PHASE_BANNER_DURATION_MS;
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
          const nextPhase = this.getBossPhase();
          if (this.bossPhase !== nextPhase) {
            this.bossPhase = nextPhase;
            this.lastPatternTs = timestamp;
            this.lastAimedPatternTs = timestamp;
            this.lastSpellPatternTs = timestamp;
            this.showPhaseBanner(nextPhase);
            this.penaltyStatus.set(`Spell Card Phase ${nextPhase} activated.`);
          }
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
    if (this.bossRewardClaimed || this.bossRewardInFlight || this.bossDefeatAnimating) {
      return;
    }

    this.boss.alive = false;
    this.bossDefeatAnimating = true;
    this.bossDefeatOrigin.x = this.boss.x;
    this.bossDefeatOrigin.y = this.boss.y;
    this.bossDefeatAnimationEndTs = performance.now() + FreeMoneyComponent.BOSS_DEFEAT_ANIMATION_MS;
    this.lastBossDefeatPulseTs = 0;
    this.player.invulnerableUntilTs = Math.max(this.player.invulnerableUntilTs, this.bossDefeatAnimationEndTs + 100);
    this.bullets = [];
    this.spawnBossDefeatBurst(24, 145, 260, 8);
    this.penaltyStatus.set('Boss core destabilizing...');
  }

  private finalizeBossDefeat(): void {
    this.bossDefeated.set(true);
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
        this.penaltyStatus.set(`Boss defeated. Reward applied: ${result.ticker} x${result.multiplier.toFixed(1)} to ${result.quantityAfter.toFixed(4)}.`);
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

    for (const bullet of this.bullets) {
      this.drawEnemyBullet(context, bullet);
    }

    if (this.boss.alive) {
      this.drawBossSprite(context, performance.now());
    }

    for (const shot of this.shots) {
      this.drawPlayerShot(context, shot);
    }

    context.fillStyle = '#51ff6a';
    for (const particle of this.collisionParticles) {
      context.beginPath();
      context.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2);
      context.fill();
    }

    if (this.player.active) {
      this.drawPlayerSprite(context, performance.now());
    }

    this.drawBossHealthBar(context);
    this.drawHitLossOverlay(context);
    this.drawPhaseBanner(context, performance.now());

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
        context.fillText(this.bossDefeated() ? 'Boss Defeated' : 'Click the screen to start', this.width / 2, this.height / 2);
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

  private drawBossSprite(context: CanvasRenderingContext2D, nowTs: number): void {
    const pulse = 1 + (Math.sin(nowTs / 230) * 0.05);

    context.save();
    context.translate(this.boss.x, this.boss.y);

    const aura = context.createRadialGradient(0, 0, this.boss.radius * 0.2, 0, 0, this.boss.radius * 1.9);
    aura.addColorStop(0, 'rgba(255, 166, 225, 0.45)');
    aura.addColorStop(0.55, 'rgba(255, 95, 182, 0.28)');
    aura.addColorStop(1, 'rgba(255, 95, 182, 0)');
    context.fillStyle = aura;
    context.beginPath();
    context.arc(0, 0, this.boss.radius * 1.9 * pulse, 0, Math.PI * 2);
    context.fill();

    context.rotate(nowTs / 1400);
    context.strokeStyle = 'rgba(255, 224, 247, 0.6)';
    context.lineWidth = 2;
    context.beginPath();
    context.moveTo(-this.boss.radius * 0.95, 0);
    context.lineTo(this.boss.radius * 0.95, 0);
    context.moveTo(0, -this.boss.radius * 0.95);
    context.lineTo(0, this.boss.radius * 0.95);
    context.stroke();

    context.rotate(-(nowTs / 1400));
    const body = context.createRadialGradient(
      -this.boss.radius * 0.28,
      -this.boss.radius * 0.32,
      this.boss.radius * 0.1,
      0,
      0,
      this.boss.radius
    );
    body.addColorStop(0, '#ffd6f0');
    body.addColorStop(0.45, '#ff74bc');
    body.addColorStop(1, '#a50f56');
    context.fillStyle = body;
    context.beginPath();
    context.arc(0, 0, this.boss.radius, 0, Math.PI * 2);
    context.fill();

    context.strokeStyle = 'rgba(255, 242, 250, 0.92)';
    context.lineWidth = 2.2;
    context.beginPath();
    context.arc(0, 0, this.boss.radius * 0.72, 0, Math.PI * 2);
    context.stroke();

    context.fillStyle = '#fff4fb';
    context.beginPath();
    context.ellipse(-this.boss.radius * 0.28, -this.boss.radius * 0.4, this.boss.radius * 0.22, this.boss.radius * 0.16, -0.45, 0, Math.PI * 2);
    context.fill();

    context.restore();
  }

  private drawPlayerSprite(context: CanvasRenderingContext2D, nowTs: number): void {
    const isInvulnerable = this.player.invulnerableUntilTs > nowTs;

    context.save();
    context.translate(this.player.x, this.player.y);

    const glow = context.createRadialGradient(0, 0, this.player.radius * 0.2, 0, 0, this.player.radius * 2.3);
    glow.addColorStop(0, 'rgba(229, 255, 176, 0.46)');
    glow.addColorStop(0.55, 'rgba(128, 255, 121, 0.28)');
    glow.addColorStop(1, 'rgba(128, 255, 121, 0)');
    context.fillStyle = glow;
    context.beginPath();
    context.arc(0, 0, this.player.radius * 2.3, 0, Math.PI * 2);
    context.fill();

    const body = context.createRadialGradient(
      -this.player.radius * 0.25,
      -this.player.radius * 0.32,
      this.player.radius * 0.2,
      0,
      0,
      this.player.radius
    );
    body.addColorStop(0, '#f8ffce');
    body.addColorStop(0.55, '#cbff5c');
    body.addColorStop(1, '#54a415');
    context.fillStyle = body;
    context.beginPath();
    context.arc(0, 0, this.player.radius, 0, Math.PI * 2);
    context.fill();

    context.strokeStyle = isInvulnerable ? '#fff7a1' : '#ffffff';
    context.lineWidth = 2.2;
    context.beginPath();
    context.arc(0, 0, this.player.radius + 1.6, 0, Math.PI * 2);
    context.stroke();

    context.fillStyle = 'rgba(255, 255, 255, 0.9)';
    context.beginPath();
    context.ellipse(-this.player.radius * 0.2, -this.player.radius * 0.35, this.player.radius * 0.22, this.player.radius * 0.16, -0.35, 0, Math.PI * 2);
    context.fill();

    if (this.isFocused()) {
      context.beginPath();
      context.arc(0, 0, this.player.hitboxRadius + 1.5, 0, Math.PI * 2);
      context.strokeStyle = '#ff2f2f';
      context.lineWidth = 1.5;
      context.stroke();

      context.beginPath();
      context.arc(0, 0, this.player.hitboxRadius, 0, Math.PI * 2);
      context.fillStyle = '#ff2f2f';
      context.fill();
    }

    context.restore();
  }

  private drawEnemyBullet(context: CanvasRenderingContext2D, bullet: Bullet): void {
    context.save();
    context.translate(bullet.x, bullet.y);
    context.rotate(Math.atan2(bullet.vy, bullet.vx) + (Math.PI / 2));

    const glow = context.createRadialGradient(0, 0, bullet.radius * 0.25, 0, 0, bullet.radius * 2.2);
    glow.addColorStop(0, 'rgba(255, 170, 200, 0.95)');
    glow.addColorStop(0.55, 'rgba(255, 78, 142, 0.62)');
    glow.addColorStop(1, 'rgba(255, 78, 142, 0)');
    context.fillStyle = glow;
    context.beginPath();
    context.arc(0, 0, bullet.radius * 2.2, 0, Math.PI * 2);
    context.fill();

    const body = context.createLinearGradient(0, -bullet.radius * 1.5, 0, bullet.radius * 1.5);
    body.addColorStop(0, '#ffe8f5');
    body.addColorStop(0.45, '#ff80b8');
    body.addColorStop(1, '#d61f70');
    context.fillStyle = body;
    context.beginPath();
    context.ellipse(0, 0, bullet.radius * 0.75, bullet.radius * 1.45, 0, 0, Math.PI * 2);
    context.fill();

    context.strokeStyle = 'rgba(255, 245, 252, 0.9)';
    context.lineWidth = 1;
    context.beginPath();
    context.ellipse(0, 0, bullet.radius * 0.5, bullet.radius * 1.1, 0, 0, Math.PI * 2);
    context.stroke();

    context.restore();
  }

  private drawPlayerShot(context: CanvasRenderingContext2D, shot: Shot): void {
    context.save();
    context.translate(shot.x, shot.y);

    const glow = context.createRadialGradient(0, 0, shot.radius * 0.15, 0, 0, shot.radius * 2.1);
    glow.addColorStop(0, 'rgba(210, 255, 255, 1)');
    glow.addColorStop(0.6, 'rgba(99, 231, 255, 0.6)');
    glow.addColorStop(1, 'rgba(99, 231, 255, 0)');
    context.fillStyle = glow;
    context.beginPath();
    context.arc(0, 0, shot.radius * 2.1, 0, Math.PI * 2);
    context.fill();

    const body = context.createLinearGradient(0, shot.radius * 1.6, 0, -shot.radius * 1.6);
    body.addColorStop(0, '#2da9d8');
    body.addColorStop(0.45, '#77f4ff');
    body.addColorStop(1, '#ffffff');
    context.fillStyle = body;
    context.beginPath();
    context.ellipse(0, 0, shot.radius * 0.55, shot.radius * 1.6, 0, 0, Math.PI * 2);
    context.fill();

    context.fillStyle = 'rgba(255, 255, 255, 0.95)';
    context.beginPath();
    context.arc(0, -shot.radius * 0.9, shot.radius * 0.35, 0, Math.PI * 2);
    context.fill();

    context.restore();
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

  private drawPhaseBanner(context: CanvasRenderingContext2D, nowTs: number): void {
    if (!this.activePhaseBannerText || this.gameOver() || this.bossDefeated()) {
      return;
    }

    const remainingMs = this.activePhaseBannerUntilTs - nowTs;
    if (remainingMs <= 0) {
      this.activePhaseBannerText = null;
      return;
    }

    const progress = 1 - (remainingMs / FreeMoneyComponent.PHASE_BANNER_DURATION_MS);
    const alphaRampIn = Math.min(1, progress / 0.2);
    const alphaRampOut = Math.min(1, remainingMs / 550);
    const alpha = Math.min(alphaRampIn, alphaRampOut);

    context.save();
    context.globalAlpha = 0.25 * alpha;
    context.fillStyle = '#19061f';
    context.fillRect(46, 140, this.width - 92, 56);

    context.globalAlpha = alpha;
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.font = '800 32px ui-sans-serif, system-ui';
    context.lineWidth = 4;
    context.strokeStyle = 'rgba(20, 4, 23, 0.9)';
    context.strokeText(this.activePhaseBannerText, this.width / 2, 168);
    context.fillStyle = '#ffd6f3';
    context.fillText(this.activePhaseBannerText, this.width / 2, 168);
    context.restore();
  }

  private resetState(): void {
    const config = FreeMoneyComponent.DIFFICULTY_CONFIG[this.selectedDifficulty()];

    this.score.set(0);
    this.hits.set(0);
    this.bombsLeft.set(3);
    this.holdingAfterHit.set(null);
    this.totalMoneyLost.set(0);
    this.bossHealth.set(config.health);
    this.bossMaxHealth.set(config.health);
    this.bossDefeated.set(false);
    this.gameOver.set(false);

    this.boss.health = config.health;
    this.boss.maxHealth = config.health;
    this.boss.alive = true;
    this.boss.x = this.width / 2;
    this.boss.y = 96;

    this.bossPatternAngle = 0;
    this.bossPhase = 1;
    this.bossPhaseCount = config.phaseCount;
    this.bossDefeatAnimating = false;
    this.bossDefeatAnimationEndTs = 0;
    this.lastBossDefeatPulseTs = 0;
    this.bossDefeatOrigin.x = this.width / 2;
    this.bossDefeatOrigin.y = 96;
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
    this.activePhaseBannerText = null;
    this.activePhaseBannerUntilTs = 0;

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
