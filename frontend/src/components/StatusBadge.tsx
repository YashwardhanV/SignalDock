import type { DeliveryStatus } from "../types";

const LABELS: Record<DeliveryStatus, string> = {
  PENDING: "Queued",
  PROCESSING: "Sending",
  RETRY_PENDING: "Retrying",
  DELIVERED: "Delivered",
  DEAD: "Dead",
};

export function StatusBadge({ status }: { status: DeliveryStatus }) {
  return (
    <span className={`status-pill status-${status.toLowerCase()}`}>
      {LABELS[status]}
    </span>
  );
}
