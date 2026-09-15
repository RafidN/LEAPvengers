import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ButtonComponent } from '../../shared/button/button.component';

@Component({
  selector: 'app-sign-in',
  styleUrls: ['./sign-in.component.css'],
  templateUrl: './sign-in.component.html',
  imports: [RouterModule, ButtonComponent]
})
export class SignInComponent {
  email = '';
  password = '';
  rememberMe = false;

  onSubmit() {
    console.log('Login submitted:', this.email, this.password);
  }
}
