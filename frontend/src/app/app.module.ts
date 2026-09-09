// app.module.ts
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AppComponent } from './app.component';
import { SignInComponent } from './pages/sign-in/sign-in.component';
import { ButtonModule } from './shared/button/button.module';
import { routes } from './app.routes';

@NgModule({
  declarations: [],
  imports: [BrowserModule, CommonModule, FormsModule, RouterModule.forRoot(routes), ButtonModule, AppComponent, SignInComponent],
  bootstrap: [SignInComponent]
})
export class AppModule {}
