import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Badge } from "../Badge";

describe("Badge", () => {
  it("renders text content correctly", () => {
    render(<Badge>OPEN</Badge>);
    expect(screen.getByText("OPEN")).toBeInTheDocument();
  });

  it("assigns critical class to OPEN, TRIGGERED, and CRITICAL", () => {
    const { rerender } = render(<Badge>OPEN</Badge>);
    expect(screen.getByText("OPEN")).toHaveClass("badge-critical");

    rerender(<Badge>TRIGGERED</Badge>);
    expect(screen.getByText("TRIGGERED")).toHaveClass("badge-critical");

    rerender(<Badge>CRITICAL</Badge>);
    expect(screen.getByText("CRITICAL")).toHaveClass("badge-critical");
  });

  it("assigns healthy class to RESOLVED, UP, and ACTIVE", () => {
    const { rerender } = render(<Badge>RESOLVED</Badge>);
    expect(screen.getByText("RESOLVED")).toHaveClass("badge-healthy");

    rerender(<Badge>UP</Badge>);
    expect(screen.getByText("UP")).toHaveClass("badge-healthy");

    rerender(<Badge>ACTIVE</Badge>);
    expect(screen.getByText("ACTIVE")).toHaveClass("badge-healthy");
  });

  it("assigns warning class to INVESTIGATING and ACKNOWLEDGED", () => {
    const { rerender } = render(<Badge>INVESTIGATING</Badge>);
    expect(screen.getByText("INVESTIGATING")).toHaveClass("badge-warning");

    rerender(<Badge>ACKNOWLEDGED</Badge>);
    expect(screen.getByText("ACKNOWLEDGED")).toHaveClass("badge-warning");
  });

  it("respects explicit variant prop override", () => {
    render(<Badge variant="neutral">CUSTOM</Badge>);
    expect(screen.getByText("CUSTOM")).toHaveClass("badge-neutral");
  });
});
