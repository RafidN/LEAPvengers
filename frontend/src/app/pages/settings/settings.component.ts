import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideArrowLeft, lucideCircleUserRound, lucideCoins, lucideShield, lucideStar } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { AuthService } from '../../services/auth.service';
import { Theme, ThemeService } from '../../services/theme.service';
import { Currency, Language, Mode, PreferencesService, ProfileIcon } from '../../services/preferences.service';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  imports: [FormsModule, RouterLink, NgIcon, HlmButtonImports, HlmCardImports, HlmInputImports, HlmLabelImports],
  providers: [provideIcons({ lucideArrowLeft, lucideCircleUserRound, lucideCoins, lucideShield, lucideStar })]
})
export class SettingsComponent {
  private readonly authService = inject(AuthService);
  protected readonly themeService = inject(ThemeService);
  protected readonly preferences = inject(PreferencesService);

  protected readonly languages: { value: Language; label: string }[] = [
    { value: 'en', label: 'English' },
    { value: 'es', label: 'Español' },
    { value: 'fr', label: 'Français' },
    { value: 'de', label: 'Deutsch' }
  ];
  protected readonly currencies: { value: Currency; label: string }[] = [
    { value: 'USD', label: 'US Dollar ($)' },
    { value: 'EUR', label: 'Euro (€)' },
    { value: 'GBP', label: 'Pound Sterling (£)' },
    { value: 'CAD', label: 'Canadian Dollar (CA$)' },
    { value: 'AUD', label: 'Australian Dollar (A$)' }
  ];
  protected readonly profileIcons: { value: ProfileIcon; label: string }[] = [
    { value: 'user', label: 'Person' },
    { value: 'shield', label: 'Shield' },
    { value: 'star', label: 'Star' },
    { value: 'money', label: 'Money' }
  ];

  protected get defaultName(): string {
    return this.authService.getFirstName() || this.authService.getUsername() || '';
  }

  protected get selectedProfileIcon(): { name: string; label: string } {
    const icon = this.profileIcons.find(option => option.value === this.preferences.profileIcon());
    return {
      name: {
        user: 'lucideCircleUserRound',
        shield: 'lucideShield',
        star: 'lucideStar',
        money: 'lucideCoins'
      }[this.preferences.profileIcon()],
      label: icon?.label ?? 'Person'
    };
  }

  protected setTheme(value: string): void {
    if (this.themeService.themes.includes(value as Theme)) {
      this.themeService.setTheme(value as Theme);
    }
  }

  protected setLanguage(value: string): void {
    if (this.languages.some(language => language.value === value)) {
      this.preferences.setLanguage(value as Language);
    }
  }

  protected setCurrency(value: string): void {
    if (this.currencies.some(currency => currency.value === value)) {
      this.preferences.setCurrency(value as Currency);
    }
  }

  protected setProfileIcon(value: string): void {
    if (this.profileIcons.some(icon => icon.value === value)) {
      this.preferences.setProfileIcon(value as ProfileIcon);
    }
  }

  protected setMode(mode: string): void {
    if (mode === 'easy' || mode === 'advanced') {
      this.preferences.setMode(mode as Mode);
    }
  }
}
