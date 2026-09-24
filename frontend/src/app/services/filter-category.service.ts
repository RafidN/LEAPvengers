/**
 * Service for filtering categories
 * Handles HTTP requests related to category filtering
 */

import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

export interface CategoryFilterRequest {
  category: string;
}

@Injectable({
  providedIn: 'root'
})



export class FilterCategoryService {
    private readonly BASE_URL = '/api/categories';


  constructor(private http: HttpClient) {}

  filterCategories(filter: CategoryFilterRequest): Observable<any> {
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.http.post<any>(`${this.BASE_URL}/filter`, filter, { headers })
      .pipe(
        catchError((error) => {
          console.error('Error filtering categories:', error);
          return throwError(error);
        })
      );
  }
}