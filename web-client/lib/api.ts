export type Status = "NEW" | "VERIFIED" | "SUSPICIOUS" | "BLOCKED";
export type Party = {
  id: string;
  type: string;
  name: string;
  shortName?: string;
  inn: string;
  kpp?: string;
  ogrn?: string;
  legalAddress?: string;
  contactEmail?: string;
  contactPhone?: string;
  verificationStatus: Status;
  createdAt: string;
};
export type Bank = {
  id: string;
  accountNumber: string;
  bik: string;
  bankName: string;
  primary: boolean;
  active: boolean;
};
export type Detail = { counterparty: Party; bankAccounts: Bank[] };
export type History = {
  id: string;
  newStatus: Status;
  oldStatus?: Status;
  reason?: string;
  changedAt: string;
  changedBy: string;
};
export type User = { id: string; phoneNumber: string; roles: string[] };
export type Auth = { accessToken: string; refreshToken: string; user: User };
export type Page = {
  content: Party[];
  page: { totalElements: number; totalPages: number; number: number };
};
let session: Auth | null = null;
let refresh: Promise<void> | null = null;
export function setSession(auth: Auth | null) {
  session = auth;
}
export async function request<T>(
  path: string,
  init: RequestInit = {},
  retry = true,
): Promise<T> {
  const snapshot = session;
  let response: Response;
  try {
    response = await fetch("/api/v1" + path, {
      ...init,
      cache: "no-store",
      headers: {
        "Content-Type": "application/json",
        ...(snapshot
          ? { Authorization: "Bearer " + snapshot.accessToken }
          : {}),
        ...init.headers,
      },
    });
  } catch {
    throw new Error("Нет соединения со шлюзом. Проверьте запуск сервисов.");
  }
  if (
    response.status === 401 &&
    snapshot &&
    retry &&
    !path.startsWith("/auth/")
  ) {
    // Another request may already have rotated this access/refresh pair.
    if (session && session !== snapshot) return request<T>(path, init, false);
    if (!refresh) {
      refresh = (async () => {
        const rotated = await request<Auth>(
          "/auth/refresh",
          {
            method: "POST",
            body: JSON.stringify({ refreshToken: snapshot.refreshToken }),
          },
          false,
        );
        if (session === snapshot) session = rotated;
      })().finally(() => {
        refresh = null;
      });
    }
    try {
      await refresh;
    } catch {
      if (session === snapshot) {
        session = null;
        window.dispatchEvent(new Event("session-expired"));
      }
      throw new Error("Сессия истекла. Войдите снова.");
    }
    if (!session) throw new Error("Сессия завершена");
    return request<T>(path, init, false);
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    const fields = error.fieldErrors
      ? Object.values(error.fieldErrors).join(". ")
      : "";
    if (response.status === 401 && !path.startsWith("/auth/"))
      window.dispatchEvent(new Event("session-expired"));
    throw new Error(
      fields ||
        error.message ||
        (response.status === 403
          ? "Недостаточно прав"
          : "Ошибка сервиса (" + response.status + ")"),
    );
  }
  if (response.status === 204) return undefined as T;
  const body = await response.text();
  return body ? JSON.parse(body) as T : undefined as T;
}
export async function logout() {
  if (refresh) await refresh.catch(() => undefined);
  const token = session?.refreshToken;
  try {
    if (token)
      await request(
        "/auth/logout",
        { method: "POST", body: JSON.stringify({ refreshToken: token }) },
        false,
      );
  } finally {
    session = null;
  }
}
export const labels: Record<Status, string> = {
  NEW: "Новый",
  VERIFIED: "Проверен",
  SUSPICIOUS: "Требует внимания",
  BLOCKED: "Заблокирован",
};
export const types: Record<string, string> = {
  LEGAL_ENTITY: "Юридическое лицо",
  INDIVIDUAL_ENTREPRENEUR: "ИП",
  SELF_EMPLOYED: "Самозанятый",
};
