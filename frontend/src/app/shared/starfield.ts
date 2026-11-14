import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, afterNextRender, inject, input } from '@angular/core';

interface Star {
  x: number;
  y: number;
  r: number;
  base: number;
  speed: number;
  phase: number;
}

/**
 * Decorative twinkling starfield on a canvas. Static when the user prefers reduced motion, and
 * paused while the tab is hidden. Hidden from assistive technology.
 */
@Component({
  selector: 'bs-starfield',
  template: `<canvas aria-hidden="true"></canvas>`,
  styles: `
    :host { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
    canvas { width: 100%; height: 100%; display: block; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Starfield {
  /** Stars per 10,000 square pixels. */
  readonly density = input(0.9);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const canvas = this.host.nativeElement.querySelector('canvas')!;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      const still = matchMedia('(prefers-reduced-motion: reduce)').matches;
      let stars: Star[] = [];
      let frame = 0;
      let width = 0;
      let height = 0;

      const resize = () => {
        const dpr = Math.min(devicePixelRatio || 1, 2);
        width = canvas.clientWidth;
        height = canvas.clientHeight;
        canvas.width = width * dpr;
        canvas.height = height * dpr;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        const count = Math.round(((width * height) / 10000) * this.density());
        stars = Array.from({ length: count }, () => ({
          x: Math.random() * width,
          y: Math.random() * height,
          r: Math.random() < 0.08 ? 1.3 : Math.random() * 0.8 + 0.3,
          base: Math.random() * 0.5 + 0.15,
          speed: Math.random() * 0.0015 + 0.0005,
          phase: Math.random() * Math.PI * 2,
        }));
        draw(0);
      };

      const draw = (t: number) => {
        ctx.clearRect(0, 0, width, height);
        for (const s of stars) {
          const alpha = still ? s.base : s.base + Math.sin(t * s.speed + s.phase) * 0.25;
          ctx.globalAlpha = Math.max(0.05, Math.min(1, alpha));
          ctx.beginPath();
          ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
          ctx.fillStyle = '#ffffff';
          ctx.fill();
        }
      };

      const loop = (t: number) => {
        if (!document.hidden) draw(t);
        frame = requestAnimationFrame(loop);
      };

      const observer = new ResizeObserver(resize);
      observer.observe(canvas);
      resize();
      if (!still) frame = requestAnimationFrame(loop);

      destroyRef.onDestroy(() => {
        cancelAnimationFrame(frame);
        observer.disconnect();
      });
    });
  }
}
