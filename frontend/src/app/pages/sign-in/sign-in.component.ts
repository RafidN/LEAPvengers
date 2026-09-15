import { Component } from '@angular/core';
import { ButtonComponent } from '../../shared/button/button.component';

@Component({
  selector: 'app-sign-in',
  styleUrls: ['./sign-in.component.css'],
  templateUrl: './sign-in.component.html',
  imports: [ButtonComponent]
})
export class SignInComponent {
  email = '';
  password = '';
  rememberMe = false;

  onSubmit() {
    console.log('Login submitted:', this.email, this.password);
  }
}
