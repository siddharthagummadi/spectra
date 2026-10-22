import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { AlertBanner } from "../AlertBanner";

describe("AlertBanner", () => {
  it("renders message with appropriate variant class", () => {
    render(<AlertBanner message="Something went wrong" variant="error" />);
    expect(screen.getByText("Something went wrong")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveClass("alert-banner-error");
  });

  it("renders field error key-value list", () => {
    render(
      <AlertBanner
        message="Validation Failed"
        fieldErrors={{ email: "Must be a valid email", name: "Name is required" }}
      />
    );
    expect(screen.getByText(/Must be a valid email/)).toBeInTheDocument();
    expect(screen.getByText(/Name is required/)).toBeInTheDocument();
  });

  it("triggers onClose callback when close button is clicked", () => {
    const handleClose = vi.fn();
    render(<AlertBanner message="Test error" onClose={handleClose} />);
    screen.getByLabelText("Dismiss alert").click();
    expect(handleClose).toHaveBeenCalledTimes(1);
  });

  it("renders nothing when neither message nor fieldErrors are provided", () => {
    const { container } = render(<AlertBanner message="" />);
    expect(container.firstChild).toBeNull();
  });
});
