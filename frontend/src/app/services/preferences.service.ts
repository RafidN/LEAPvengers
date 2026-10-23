import { Injectable, signal } from '@angular/core';


export type Language = 'en' | 'es' | 'fr' | 'de';
export type Currency = 'USD' | 'EUR' | 'GBP' | 'CAD' | 'AUD';
export type ProfileIcon = 'user' | 'shield' | 'star' | 'money';
export type Mode = 'easy' | 'advanced'; 

const STORAGE_KEY = 'user-preferences';

interface StoredPreferences {
  newsAlerts: boolean;
  advancedMode: boolean;
  language: Language;
  currency: Currency;
  displayName: string;
  profileIcon: ProfileIcon;
  mode: Mode; 
}

const DEFAULT_PREFERENCES: StoredPreferences = {
  newsAlerts: true,
  advancedMode: false,
  language: 'en',
  currency: 'USD',
  displayName: '',
  profileIcon: 'user',
  mode: 'easy'
};

@Injectable({ providedIn: 'root' })
export class PreferencesService {
  readonly newsAlerts = signal(this.load().newsAlerts);
  readonly advancedMode = signal(this.load().advancedMode);
  readonly language = signal(this.load().language);
  readonly currency = signal(this.load().currency);
  readonly displayName = signal(this.load().displayName);
  readonly profileIcon = signal(this.load().profileIcon);
  readonly mode = signal(this.load().mode);

  setNewsAlerts(enabled: boolean): void {
    this.newsAlerts.set(enabled);
    this.save();
  }

  setAdvancedMode(enabled: boolean): void {
    this.advancedMode.set(enabled);
    this.mode.set(enabled ? 'advanced' : 'easy');
    this.save();
  }

  setLanguage(language: Language): void {
    this.language.set(language);
    this.save();
  }

  setCurrency(currency: Currency): void {
    this.currency.set(currency);
    this.save();
  }

  setDisplayName(displayName: string): void {
    this.displayName.set(displayName.trim());
    this.save();
  }

  setProfileIcon(profileIcon: ProfileIcon): void {
    this.profileIcon.set(profileIcon);
    this.save();
  }

  setMode(mode: Mode): void {
    this.mode.set(mode);
    this.advancedMode.set(mode === 'advanced');
    this.save();
  }

  private load(): StoredPreferences {
    if (typeof localStorage === 'undefined') {
      return DEFAULT_PREFERENCES;
    }
    try {
      const stored = JSON.parse(localStorage.getItem(this.storageKey()) ?? '{}') as Partial<StoredPreferences>;
      return {
        newsAlerts: typeof stored.newsAlerts === 'boolean' ? stored.newsAlerts : DEFAULT_PREFERENCES.newsAlerts,
        advancedMode: typeof stored.advancedMode === 'boolean' ? stored.advancedMode : DEFAULT_PREFERENCES.advancedMode,
        language: this.isLanguage(stored.language) ? stored.language : DEFAULT_PREFERENCES.language,
        currency: this.isCurrency(stored.currency) ? stored.currency : DEFAULT_PREFERENCES.currency,
        displayName: typeof stored.displayName === 'string' ? stored.displayName : DEFAULT_PREFERENCES.displayName,
        profileIcon: this.isProfileIcon(stored.profileIcon) ? stored.profileIcon : DEFAULT_PREFERENCES.profileIcon,
        mode: this.isMode(stored.mode)
          ? stored.mode
          : stored.advancedMode === true ? 'advanced' : DEFAULT_PREFERENCES.mode
      };
    } catch {
      return DEFAULT_PREFERENCES;
    }
  }

  private save(): void {
    if (typeof localStorage === 'undefined') {
      return;
    }
    try {
      localStorage.setItem(this.storageKey(), JSON.stringify({
        newsAlerts: this.newsAlerts(),
        advancedMode: this.advancedMode(),
        language: this.language(),
        currency: this.currency(),
        displayName: this.displayName(),
        profileIcon: this.profileIcon(),
        mode: this.mode()
      }));
    } catch {
      // Preferences still apply for this visit if storage is unavailable.
    }
  }

  private storageKey(): string {
    const username = localStorage.getItem('username');
    return username ? `${STORAGE_KEY}:${username}` : STORAGE_KEY;
  }

  private isLanguage(value: unknown): value is Language {
    return value === 'en' || value === 'es' || value === 'fr' || value === 'de';
  }

  private isCurrency(value: unknown): value is Currency {
    return value === 'USD' || value === 'EUR' || value === 'GBP' || value === 'CAD' || value === 'AUD';
  }

  private isProfileIcon(value: unknown): value is ProfileIcon {
    return value === 'user' || value === 'shield' || value === 'star' || value === 'money';
  }

  private isMode(value: unknown): value is Mode {
    return value === 'easy' || value === 'advanced';
  }
}
