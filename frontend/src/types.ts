export type DeliveryStatus =
  | "PENDING"
  | "PROCESSING"
  | "RETRY_PENDING"
  | "DELIVERED"
  | "DEAD";

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};

export type Endpoint = {
  id: string;
  name: string;
  url: string;
  active: boolean;
  maxAttempts: number;
  secretHint: string;
  createdAt: string;
  updatedAt: string;
};

export type Subscription = {
  id: string;
  endpointId: string;
  endpointName: string;
  eventPattern: string;
  active: boolean;
  createdAt: string;
};

export type EventRecord = {
  id: string;
  eventType: string;
  payload: unknown;
  idempotencyKey: string;
  createdAt: string;
};

export type Delivery = {
  id: string;
  eventId: string;
  eventType: string;
  endpointId: string;
  endpointName: string;
  endpointUrl: string;
  status: DeliveryStatus;
  attemptCount: number;
  maxAttempts: number;
  nextRetryAt: string;
  lastError: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type DeliveryAttempt = {
  id: string;
  attemptNumber: number;
  startedAt: string;
  finishedAt: string;
  httpStatus: number | null;
  responseBody: string | null;
  errorMessage: string | null;
  latencyMs: number;
};

export type DeliveryDetail = {
  delivery: Delivery;
  attempts: DeliveryAttempt[];
};

export type DashboardSummary = {
  total: number;
  byStatus: Record<DeliveryStatus, number>;
  successRate: number;
};

export type Snapshot = {
  summary: DashboardSummary;
  endpoints: Endpoint[];
  subscriptions: Subscription[];
  events: EventRecord[];
  deliveries: Delivery[];
};
