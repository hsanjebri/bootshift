import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { App } from './app';
import { PaletteControl } from './core/palette-control';

describe('App root', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('opens the command palette with Ctrl+K from anywhere', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const control = TestBed.inject(PaletteControl);
    const before = control.openRequests();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }));

    expect(control.openRequests()).toBe(before + 1);
  });

  it('ignores shortcuts while the user is typing', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const input = document.createElement('input');
    document.body.appendChild(input);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', bubbles: true }));
    expect(navigate).not.toHaveBeenCalled();

    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', bubbles: true }));
    expect(navigate).toHaveBeenCalledWith(['/new']);
    input.remove();
  });
});
