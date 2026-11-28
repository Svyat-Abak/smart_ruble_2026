"use client";
import { useEffect, useState, useRef, FormEvent } from "react";
import {
  Plus,
  RefreshCw,
  X,
  CheckCircle2,
  Wallet,
  Clock3,
  AlertTriangle,
} from "lucide-react";
import { request, User, Party, Page } from "../lib/api";

type Line = {
  id: string;
  category: string;
  limit_amount: number;
  reserved_amount: number;
  spent_amount: number;
  available: number;
};
type Order = {
  id: string;
  title: string;
  work_object: string;
  contractor_id: string;
  status: string;
  total_budget: number;
  lines: Line[];
};
type Payment = {
  id: string;
  work_order_id: string;
  budget_line_id: string;
  recipient_id: string;
  recipient_name: string;
  amount: number;
  purpose: string;
  status: string;
  reason: string;
  attempts: number;
  last_error?: string;
  history?: {
    id: string;
    status: string;
    reason: string;
    changed_at: string;
  }[];
};
type Notice = {
  id: string;
  payment_id: string;
  status: string;
  message: string;
  received_at: string;
};
const money = (n: number) =>
  new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    maximumFractionDigits: 2,
  }).format(n);
const labels: Record<string, string> = {
  DRAFT: "Черновик",
  ACTIVE: "В работе",
  CLOSED: "Закрыто",
  READY: "Готов к отправке",
  PENDING_APPROVAL: "На согласовании",
  PROCESSING: "Ожидаем банк",
  SUCCEEDED: "Оплачен",
  FAILED: "Ошибка банка",
  BLOCKED: "Заблокирован",
  REJECTED: "Отклонён",
};
const categories: Record<string, string> = {
  MATERIALS: "Материалы",
  LABOR: "Работы",
  EQUIPMENT: "Оборудование",
  TRANSPORT: "Транспорт",
  OTHER: "Прочее",
};
function Status({ value }: { value: string }) {
  return (
    <span
      className={
        "badge " +
        (["SUCCEEDED", "ACTIVE"].includes(value)
          ? "VERIFIED"
          : ["BLOCKED", "FAILED", "REJECTED"].includes(value)
            ? "BLOCKED"
            : "SUSPICIOUS")
      }
    >
      {labels[value] || value}
    </span>
  );
}
function Dialog({
  title,
  close,
  children,
}: {
  title: string;
  close: () => void;
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
  }, []);
  return (
    <dialog ref={ref} onCancel={close}>
      <div className="dialog-head">
        <h2>{title}</h2>
        <button className="icon" aria-label="Закрыть" onClick={close}>
          <X />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export default function Workbench({
  section,
  user,
}: {
  section: string;
  user: User;
}) {
  const [orders, setOrders] = useState<Order[]>([]),
    [payments, setPayments] = useState<Payment[]>([]),
    [parties, setParties] = useState<Party[]>([]),
    [notices, setNotices] = useState<Notice[]>([]);
  const [error, setError] = useState(""),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [feedback, setFeedback] = useState("");
  const [selected, setSelected] = useState<string | null>(null),
    [form, setForm] = useState<
      "order" | "payment" | "decision" | "assign" | "member" | null
    >(null),
    [payment, setPayment] = useState<Payment | null>(null);
  const [decision, setDecision] = useState(true),
    [formError, setFormError] = useState("");
  const key = useRef("");
  const approve = user.roles.some((r) =>
    ["ROLE_ADMIN", "ROLE_FINANCE_DIRECTOR"].includes(r),
  );
  const manage = user.roles.some((r) =>
    ["ROLE_ADMIN", "ROLE_MANAGER"].includes(r),
  );
  const pay = user.roles.some((r) =>
    ["ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CONTRACTOR"].includes(r),
  );
  const active = orders.find((o) => o.id === selected);
  async function load() {
    try {
      const data = await request<{ workorders: Order[]; payments: Payment[] }>(
        "/billing/reports/overview",
      );
      setOrders(data.workorders);
      setPayments(data.payments);
      if (section === "notifications" && approve)
        setNotices(await request<Notice[]>("/notifications"));
      setError("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    let active = true;
    const refresh = async () => {
      if (active) await load();
    };
    refresh();
    const timer = setInterval(refresh, 4000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [section]);
  useEffect(() => {
    request<Page>("/billing/counterparties?size=200&sort=name,asc")
      .then((p) => setParties(p.content))
      .catch((e) => setError(e.message));
  }, []);
  function open(kind: typeof form) {
    setFormError("");
    setFeedback("");
    key.current = crypto.randomUUID();
    setForm(kind);
  }
  async function transition(id: string, status: string) {
    setBusy(true);
    try {
      await request("/billing/workorders/" + id + "/status", {
        method: "POST",
        body: JSON.stringify({ status }),
      });
      await load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setFormError("");
    const f = new FormData(e.currentTarget);
    let path = "",
      body: unknown,
      headers: Record<string, string> = {};
    if (form === "order") {
      path = "/billing/workorders";
      body = {
        title: f.get("title"),
        workObject: f.get("workObject"),
        contractorId: f.get("contractorId"),
        lines: Object.keys(categories)
          .filter((c) => Number(f.get(c)) > 0)
          .map((c) => ({ category: c, limit: f.get(c) })),
      };
    }
    if (form === "payment") {
      path = "/billing/payments";
      headers = { "Idempotency-Key": key.current };
      body = {
        workOrderId: active!.id,
        budgetLineId: f.get("budgetLineId"),
        recipientId: f.get("recipientId"),
        amount: f.get("amount"),
        purpose: f.get("purpose"),
      };
    }
    if (form === "decision") {
      path = "/billing/payments/" + payment!.id + "/decision";
      body = { approve: decision, comment: f.get("comment") };
    }
    if (form === "assign") {
      path = "/billing/workorders/" + active!.id + "/assignees";
      body = { userId: f.get("userId") };
    }
    if (form === "member") {
      path = "/billing/counterparties/" + active!.contractor_id + "/members";
      body = { userId: f.get("userId") };
    }
    try {
      await request(path, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
      });
      setForm(null);
      setPayment(null);
      setFeedback("Операция сохранена");
      await load();
    } catch (e) {
      setFormError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function detail(p: Payment) {
    try {
      setPayment(await request<Payment>("/billing/payments/" + p.id));
    } catch (e) {
      setError((e as Error).message);
    }
  }
  const totals = orders
    .flatMap((o) => o.lines)
    .reduce(
      (a, l) => ({
        limit: a.limit + Number(l.limit_amount),
        reserved: a.reserved + Number(l.reserved_amount),
        spent: a.spent + Number(l.spent_amount),
      }),
      { limit: 0, reserved: 0, spent: 0 },
    );
  const titles: Record<string, string> = {
    overview: "Контроль расходов",
    workorders: "Распоряжения",
    payments: "Платёжные заявки",
    approvals: "Очередь согласования",
    notifications: "События платежей",
  };
  const visiblePayments = payments.filter(
    (p) => section !== "approvals" || p.status === "PENDING_APPROVAL",
  );
  return (
    <div className="content">
      <div className="page-heading">
        <div>
          <span className="eyebrow">SMART RUBLE / DEMO MVP</span>
          <h1>{titles[section]}</h1>
          <p className="muted">
            Банковские операции выполняет эмулятор. Валюта бюджетов — RUB.
          </p>
        </div>
        <div className="actions">
          <button aria-label="Обновить" onClick={load}>
            <RefreshCw size={17} />
          </button>
          {section === "workorders" && manage && (
            <button className="primary" onClick={() => open("order")}>
              <Plus size={18} />
              Новое распоряжение
            </button>
          )}
        </div>
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {feedback && (
        <p className="notice" role="status">
          <CheckCircle2 size={17} />
          {feedback}
        </p>
      )}
      {loading ? (
        <p>Загружаем данные…</p>
      ) : (
        <>
          {section === "overview" && (
            <>
              <div className="metric-grid">
                {[
                  ["Выделено", totals.limit],
                  ["В резерве", totals.reserved],
                  ["Оплачено", totals.spent],
                  ["Доступно", totals.limit - totals.reserved - totals.spent],
                ].map(([label, value]) => (
                  <div className="metric" key={label}>
                    <Wallet size={20} />
                    <small>{label}</small>
                    <strong>{money(Number(value))}</strong>
                  </div>
                ))}
              </div>
              <div className="registry report-list">
                <h2>Исполнение бюджетов</h2>
                {orders.length === 0 ? (
                  <p className="muted">
                    Создайте первое распоряжение и распределите бюджет по
                    статьям.
                  </p>
                ) : (
                  orders.map((o) => {
                    const spent = o.lines.reduce(
                      (s, l) => s + Number(l.spent_amount),
                      0,
                    );
                    return (
                      <div className="report-row" key={o.id}>
                        <div>
                          <strong>{o.title}</strong>
                          <small>{o.work_object}</small>
                        </div>
                        <progress max={Number(o.total_budget)} value={spent} />
                        <span>
                          {money(spent)} / {money(Number(o.total_budget))}
                        </span>
                        <Status value={o.status} />
                      </div>
                    );
                  })
                )}
              </div>
              <div className="overview-note">
                <AlertTriangle />
                <p>
                  На согласовании:{" "}
                  {
                    payments.filter((p) => p.status === "PENDING_APPROVAL")
                      .length
                  }
                  . Заблокировано правилами:{" "}
                  {payments.filter((p) => p.status === "BLOCKED").length}.
                  Ожидают подтверждения банка:{" "}
                  {payments.filter((p) => p.status === "PROCESSING").length}.
                </p>
              </div>
            </>
          )}
          {section === "workorders" && (
            <div className="order-grid">
              {orders.map((o) => (
                <button
                  key={o.id}
                  className="order-card"
                  onClick={() => setSelected(o.id)}
                >
                  <div className="section-head">
                    <Status value={o.status} />
                    <span>RUB</span>
                  </div>
                  <h2>{o.title}</h2>
                  <p>{o.work_object}</p>
                  <strong>{money(Number(o.total_budget))}</strong>
                  <small>{o.lines.length} статьи сметы</small>
                </button>
              ))}
              {!orders.length && (
                <div className="empty">
                  <Wallet />
                  <h3>Распоряжений пока нет</h3>
                  <p>Начните с проверенного подрядчика и бюджета работ.</p>
                </div>
              )}
            </div>
          )}
          {["payments", "approvals"].includes(section) && (
            <div className="registry table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Получатель / назначение</th>
                    <th>Сумма</th>
                    <th>Статус</th>
                    <th>Результат проверки</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {visiblePayments.map((p) => (
                    <tr key={p.id}>
                      <td>
                        <strong>{p.recipient_name}</strong>
                        <small>{p.purpose}</small>
                      </td>
                      <td>{money(Number(p.amount))}</td>
                      <td>
                        <Status value={p.status} />
                      </td>
                      <td className="reason-cell">{p.reason}</td>
                      <td>
                        <button onClick={() => detail(p)}>Открыть</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {visiblePayments.length === 0 && (
                <div className="empty">
                  <CheckCircle2 />
                  <h3>
                    {section === "approvals"
                      ? "Всё разобрано"
                      : "Заявок пока нет"}
                  </h3>
                  <p>
                    Заявка создаётся из карточки утверждённого распоряжения.
                  </p>
                </div>
              )}
            </div>
          )}
          {section === "notifications" && (
            <div className="registry report-list">
              <p className="muted">
                Журнал доставленных событий Kafka. Повторная доставка одного
                события сохраняет одну запись.
              </p>
              {notices.map((n) => (
                <div className="history" key={n.id}>
                  <Status value={n.status} />
                  <p>{n.message}</p>
                  <small>
                    {new Date(n.received_at).toLocaleString("ru-RU")} · Платёж{" "}
                    {n.payment_id}
                  </small>
                </div>
              ))}
              {!notices.length && (
                <p>
                  Событий пока нет. После изменения платежа обновление появится
                  здесь.
                </p>
              )}
            </div>
          )}
        </>
      )}
      {active && !form && (
        <Dialog title={active.title} close={() => setSelected(null)}>
          <div className="detail-body">
            <Status value={active.status} />
            <p>{active.work_object}</p>
            <div className="actions">
              {approve && active.status === "DRAFT" && (
                <button
                  className="primary"
                  disabled={busy}
                  onClick={() => transition(active.id, "ACTIVE")}
                >
                  Утвердить бюджет
                </button>
              )}
              {pay && active.status === "ACTIVE" && (
                <button className="primary" onClick={() => open("payment")}>
                  Создать заявку
                </button>
              )}
              {(manage || approve) && active.status === "ACTIVE" && (
                <button
                  disabled={busy}
                  onClick={() => transition(active.id, "CLOSED")}
                >
                  Завершить работы
                </button>
              )}
            </div>
            {error && <p className="error">{error}</p>}
            {active.lines.map((l) => (
              <div key={l.id} className="bank">
                <strong>{categories[l.category]}</strong>
                <p>Лимит {money(Number(l.limit_amount))}</p>
                <small>
                  Резерв {money(Number(l.reserved_amount))} · Потрачено{" "}
                  {money(Number(l.spent_amount))}
                </small>
                <p>
                  Доступно <strong>{money(Number(l.available))}</strong>
                </p>
              </div>
            ))}
            {manage && (
              <div className="actions">
                <button onClick={() => open("assign")}>
                  Назначить сотрудника
                </button>
                <button onClick={() => open("member")}>
                  Привязать представителя подрядчика
                </button>
              </div>
            )}
          </div>
        </Dialog>
      )}
      {payment && !form && (
        <Dialog title="Платёжная заявка" close={() => setPayment(null)}>
          <div className="detail-body">
            <Status value={payment.status} />
            <h2>{money(Number(payment.amount))}</h2>
            <p>
              {payment.recipient_name} · {payment.purpose}
            </p>
            <p>{payment.reason}</p>
            {payment.last_error && (
              <p className="error">{payment.last_error}</p>
            )}
            <small>Попыток отправки: {payment.attempts}</small>
            {approve && payment.status === "PENDING_APPROVAL" && (
              <div className="actions">
                <button
                  className="primary"
                  onClick={() => {
                    setDecision(true);
                    open("decision");
                  }}
                >
                  Согласовать
                </button>
                <button
                  onClick={() => {
                    setDecision(false);
                    open("decision");
                  }}
                >
                  Отклонить
                </button>
              </div>
            )}
            <h3>История решения</h3>
            {payment.history?.map((h) => (
              <div className="history" key={h.id}>
                <Status value={h.status} />
                <p>{h.reason}</p>
                <small>{new Date(h.changed_at).toLocaleString("ru-RU")}</small>
              </div>
            ))}
          </div>
        </Dialog>
      )}
      {form && (
        <Dialog
          title={
            {
              order: "Новое распоряжение",
              payment: "Заявка на оплату",
              decision: decision ? "Согласовать заявку" : "Отклонить заявку",
              assign: "Назначение сотрудника",
              member: "Представитель подрядчика",
            }[form]
          }
          close={() => {
            if (!busy) setForm(null);
          }}
        >
          <form className="detail-body" onSubmit={submit}>
            {form === "order" && (
              <>
                <label>
                  Название
                  <input
                    name="title"
                    required
                    maxLength={255}
                    placeholder="Ремонт офиса"
                  />
                </label>
                <label>
                  Объект работ
                  <input
                    name="workObject"
                    required
                    maxLength={500}
                    placeholder="Корпус А, второй этаж"
                  />
                </label>
                <label>
                  Проверенный подрядчик
                  <select name="contractorId" required>
                    <option value="">Выберите компанию</option>
                    {parties
                      .filter((p) => p.verificationStatus === "VERIFIED")
                      .map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                  </select>
                </label>
                <h3>Смета, ₽</h3>
                <p className="muted">
                  Заполните хотя бы одну статью. Общий бюджет равен сумме
                  лимитов.
                </p>
                <div className="form-grid">
                  {Object.entries(categories).map(([id, label]) => (
                    <label key={id}>
                      {label}
                      <input
                        type="number"
                        name={id}
                        min="0"
                        max="999999999999"
                        step="0.01"
                        placeholder="0,00"
                      />
                    </label>
                  ))}
                </div>
              </>
            )}
            {form === "payment" && active && (
              <>
                <label>
                  Статья сметы
                  <select name="budgetLineId" required>
                    {active.lines.map((l) => (
                      <option key={l.id} value={l.id}>
                        {categories[l.category]} · доступно{" "}
                        {money(Number(l.available))}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Получатель
                  <select name="recipientId" required>
                    <option value="">Выберите контрагента</option>
                    {parties.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Сумма, ₽
                  <input
                    name="amount"
                    type="number"
                    required
                    min="0.01"
                    max="999999999999"
                    step="0.01"
                  />
                </label>
                <label>
                  Назначение
                  <textarea
                    name="purpose"
                    maxLength={500}
                    required
                    placeholder="Материалы для ремонта"
                  />
                </label>
                <p className="muted">
                  Заявки от 50 000 ₽ и платежи непроверенным получателям
                  проходят согласование.
                </p>
              </>
            )}
            {form === "decision" && (
              <label>
                Основание решения
                <textarea name="comment" maxLength={1000} required />
              </label>
            )}
            {["assign", "member"].includes(form) && (
              <label>
                UUID пользователя
                <input name="userId" required pattern="[0-9a-fA-F-]{36}" />
                <small>
                  Пользователь должен иметь соответствующую роль в auth-service.
                </small>
              </label>
            )}
            {formError && (
              <p className="error" role="alert">
                {formError}
              </p>
            )}
            <div className="form-footer">
              <button
                type="button"
                disabled={busy}
                onClick={() => setForm(null)}
              >
                Отмена
              </button>
              <button className="primary" disabled={busy}>
                {busy ? "Сохраняем…" : "Подтвердить"}
              </button>
            </div>
          </form>
        </Dialog>
      )}
    </div>
  );
}
