const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:4000";

/** Absolute URL for opening API resources (PDFs) in a new tab. */
export function apiUrl(path: string): string {
  return `${API_BASE}/api${path}`;
}

function getCsrf(): string {
  if (typeof document === "undefined") return "";
  return document.cookie.match(/XSRF-TOKEN=([^;]+)/)?.[1] ?? "";
}

export class ApiError extends Error {
  status: number;
  fields?: Record<string, string>;
  constructor(status: number, message: string, fields?: Record<string, string>) {
    super(message);
    this.status = status;
    this.fields = fields;
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? "GET").toUpperCase();
  if (method !== "GET" && !getCsrf()) {
    // Seed the XSRF-TOKEN cookie before the first mutating request.
    await fetch(`${API_BASE}/actuator/health`, { credentials: "include" });
  }
  const res = await fetch(`${API_BASE}/api${path}`, {
    credentials: "include",
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": decodeURIComponent(getCsrf()),
      ...options.headers,
    },
  });
  if (res.status === 401 && typeof window !== "undefined" && !path.startsWith("/auth/")) {
    window.location.href = "/login";
    throw new ApiError(401, "Not authenticated");
  }
  if (!res.ok) {
    let message = "Request failed";
    let fields: Record<string, string> | undefined;
    try {
      const body = await res.json();
      message = body.message ?? message;
      fields = body.fields;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(res.status, message, fields);
  }
  if (res.status === 204 || res.headers.get("content-length") === "0") {
    return undefined as T;
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export type Role = "ADMIN" | "RECEPTIONIST" | "LAB_STAFF" | "MARKETING";

export interface Me {
  id: number;
  name: string;
  email: string;
  role: Role;
  branchId: number;
}

export interface Patient {
  id: number;
  patientNo: string;
  name: string;
  nicOrId?: string;
  dob?: string;
  /** Computed by the backend from dob; advances automatically each year. */
  age?: number;
  gender?: string;
  phone: string;
  email?: string;
  address?: string;
  specialNote?: string;
  consentEmail: boolean;
  consentWhatsapp: boolean;
  createdAt?: string;
}

export interface TemplateField {
  key: string;
  label: string;
  unit?: string;
  refLow?: number;
  refHigh?: number;
  type?: string;
}

export interface TestTemplate {
  id: number;
  name: string;
  fields: TemplateField[];
}

export interface LabTest {
  id: number;
  code: string;
  name: string;
  category: string;
  price: string | number;
  specimenType?: string;
  templateId: number;
  active: boolean;
}

export type OrderStatus = "PENDING" | "COLLECTED" | "IN_PROGRESS" | "COMPLETED" | "VERIFIED";

export interface Invoice {
  id: number;
  invoiceNo: string;
  patientId: number;
  subtotal: string | number;
  discount: string | number;
  total: string | number;
  paymentMethod: string;
  status: string;
  createdAt: string;
}

export interface InvoiceItemDetail {
  itemId: number;
  testId: number;
  testCode: string;
  testName: string;
  priceAtSale: string | number;
  orderId: number;
  orderStatus: OrderStatus;
}

export interface InvoiceDetail {
  invoice: Invoice;
  items: InvoiceItemDetail[];
}

export interface WorklistRow {
  orderId: number;
  status: OrderStatus;
  testCode: string;
  testName: string;
  patientNo: string;
  patientName: string;
  invoiceId: number;
  invoiceNo: string;
  billedAt: string;
  sampleCollectedAt?: string;
}

export interface ResultResponse {
  orderId: number;
  status: OrderStatus;
  values: Record<string, number>;
  flags: Record<string, "H" | "L">;
}

export interface DeliveryStatus {
  invoiceId: number;
  sentEmailAt: string | null;
}

export interface MailConfig {
  emailEnabled: boolean;
}

export interface AnomalyItem {
  resultId: number;
  orderId: number;
  testCode: string;
  testName: string;
  patientNo: string;
  patientName: string;
  values: Record<string, number>;
  flags: Record<string, "H" | "L">;
  enteredAt: string;
}
