<?php

namespace App\Services\SheetParsing;

final class Box
{
    public readonly float $minX;

    public readonly float $minY;

    public readonly float $maxX;

    public readonly float $maxY;

    public readonly float $midX;

    public readonly float $midY;

    public readonly bool $isEmpty;

    public function __construct(
        public readonly float $x,
        public readonly float $y,
        public readonly float $width,
        public readonly float $height,
    ) {
        $this->minX = $x;
        $this->minY = $y;
        $this->maxX = $x + $width;
        $this->maxY = $y + $height;
        $this->midX = $x + $width / 2.0;
        $this->midY = $y + $height / 2.0;
        $this->isEmpty = $width <= 0.0 || $height <= 0.0;
    }

    public function union(self $o): self
    {
        if ($this->isEmpty) {
            return $o;
        }
        if ($o->isEmpty) {
            return $this;
        }
        $x0 = min($this->minX, $o->minX);
        $y0 = min($this->minY, $o->minY);

        return new self($x0, $y0, max($this->maxX, $o->maxX) - $x0, max($this->maxY, $o->maxY) - $y0);
    }

    public function intersection(self $o): self
    {
        $x0 = max($this->minX, $o->minX);
        $y0 = max($this->minY, $o->minY);
        $x1 = min($this->maxX, $o->maxX);
        $y1 = min($this->maxY, $o->maxY);

        return ($x1 <= $x0 || $y1 <= $y0) ? self::zero() : new self($x0, $y0, $x1 - $x0, $y1 - $y0);
    }

    public function offset(float $dx = 0.0, float $dy = 0.0): self
    {
        return new self($this->x + $dx, $this->y + $dy, $this->width, $this->height);
    }

    public static function zero(): self
    {
        return new self(0.0, 0.0, 0.0, 0.0);
    }
}
