"use client";
import { useEffect, useRef, useState, FormEvent } from "react";
import {
  Building2,
  Search,
  Plus,
  ShieldCheck,
  LayoutDashboard,
  LogOut,
  ChevronLeft,
  ChevronRight,
  X,
  Landmark,
  RefreshCw,
  ArrowUpRight,
  Users,
  Check,
} from "lucide-react";
import {
  Auth,
  User,
  Party,
  Detail,
  Page,
  History,
  Status,
  request,
  setSession,
  logout,
  labels,
  types,
} from "../lib/api";

import Workbench from "./workbench";

const base = "/billing/counterparties";
const date = (s: string) =>
  new Date(s).toLocaleString("ru-RU", {
    dateStyle: "medium",
    timeStyle: "short",
  });
function Badge({ status }: { status: Status }) {
  return <span className={"badge " + status}>{labels[status]}</span>;
}
function values(form: HTMLFormElement) {
  return Object.fromEntries(
    Array.from(new FormData(form).entries()).map(([k, v]) => [
      k,
      v === "" ? null : v,
    ]),
  );
}
function Modal({
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
    <dialog
      ref={ref}
      onCancel={close}
      onClick={(e) => {
        if (e.target === e.currentTarget) close();
      }}
    >
      <div className="dialog-head">
        <h2>{title}</h2>
        <button aria-label="Закрыть" className="icon" onClick={close}>
          <X size={22} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export default function Home() {
  const [section, setSection] = useState("counterparties");
  const [user, setUser] = useState<User | null>(null);
  const [rows, setRows] = useState<Party[]>([]);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [pages, setPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState(0);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [history, setHistory] = useState<History[]>([]);
  const [modal, setModal] = useState<
    "create" | "edit" | "bank" | "status" | null
  >(null);
  const [formError, setFormError] = useState("");
  const [nextStatus, setNextStatus] = useState<Status>("VERIFIED");
  const selected = useRef(0);
  const canWrite = user?.roles.some((r) =>
    ["ROLE_MANAGER", "ROLE_FINANCE_DIRECTOR", "ROLE_ADMIN"].includes(r),
  );
  const canApprove = user?.roles.some((r) =>
    ["ROLE_FINANCE_DIRECTOR", "ROLE_ADMIN"].includes(r),
  );
  const canHistory = user?.roles.some((r) =>
    [
      "ROLE_EMPLOYEE",
      "ROLE_MANAGER",
      "ROLE_FINANCE_DIRECTOR",
      "ROLE_ADMIN",
    ].includes(r),
  );
  useEffect(() => {
    const expired = () => {
      setSession(null);
      setUser(null);
      setRows([]);
      setDetail(null);
      setModal(null);
      setError("Сессия истекла. Войдите снова.");
    };
    window.addEventListener("session-expired", expired);
    return () => window.removeEventListener("session-expired", expired);
  }, []);
  useEffect(() => {
    if (!user) return;
    let active = true;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    const timer = setTimeout(() => {
      request<Page>(
        base +
          "?" +
          new URLSearchParams({
            search,
            ...(status ? { status } : {}),
            page: String(page),
            size: "12",
            sort: "createdAt,desc",
          }),
        { signal: controller.signal },
      )
        .then((data) => {
          if (active) {
            setRows(data.content);
            setTotal(data.page.totalElements);
            setPages(data.page.totalPages);
          }
        })
        .catch((e) => {
          if (active) {
            setError(e.message);
            setRows([]);
          }
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 250);
    return () => {
      active = false;
      clearTimeout(timer);
      controller.abort();
    };
  }, [user, search, status, page, version]);
  function open(kind: typeof modal) {
    setFormError("");
    setModal(kind);
  }
  async function show(id: string) {
    const generation = ++selected.current;
    setError("");
    try {
      const data = await request<Detail>(base + "/" + id);
      const events = canHistory
        ? await request<History[]>(base + "/" + id + "/status-history")
        : [];
      if (generation === selected.current) {
        setDetail(data);
        setHistory(events);
      }
    } catch (e) {
      setError((e as Error).message);
    }
  }
  async function login(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const data = await request<Auth>("/auth/login", {
        method: "POST",
        body: JSON.stringify(values(e.currentTarget)),
      });
      setSession(data);
      setUser(data.user);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setFormError("");
    const data = values(e.currentTarget);
    const id = detail?.counterparty.id;
    const path =
      modal === "create"
        ? base
        : base +
          "/" +
          id +
          (modal === "bank"
            ? "/bank-accounts"
            : modal === "status"
              ? "/status"
              : "");
    try {
      await request(path, {
        method: modal === "edit" ? "PUT" : "POST",
        body: JSON.stringify(data),
      });
      setModal(null);
      setVersion((v) => v + 1);
      setNotice("Изменения сохранены");
      if (id && modal !== "create") await show(id);
    } catch (e) {
      setFormError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function bankAction(id: string, action: string) {
    setBusy(true);
    setError("");
    try {
      await request(
        base + "/" + detail!.counterparty.id + "/bank-accounts/" + id,
        { method: "PATCH", body: JSON.stringify({ action }) },
      );
      await show(detail!.counterparty.id);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (!user)
    return (
      <div className="login">
        <section className="login-story">
          <div className="brand">
            <span className="brand-icon">₽</span> SmartRuble
          </div>
          <div>
            <span className="eyebrow">ФИНАНСОВЫЙ КОНТРОЛЬ</span>
            <h1>
              Каждое решение.
              <br />
              Под контролем.
            </h1>
            <p>
              Бюджеты, согласование платежей и история решений в одном рабочем
              пространстве.
            </p>
            <div className="story-line">
              <ShieldCheck /> Проверяйте получателей до оплаты
            </div>
          </div>
          <small>Рабочее пространство вашей компании</small>
        </section>
        <section className="login-form">
          <div className="login-card">
            <span className="eyebrow">С ВОЗВРАЩЕНИЕМ</span>
            <h2>Вход в кабинет</h2>
            <p className="muted">Используйте учётную запись сотрудника.</p>
            <form onSubmit={login}>
              <label>
                Номер телефона
                <input
                  name="phoneNumber"
                  type="tel"
                  placeholder="+7 900 000-00-00"
                  required
                  autoComplete="username"
                />
              </label>
              <label>
                Пароль
                <input
                  name="password"
                  type="password"
                  required
                  autoComplete="current-password"
                  placeholder="Введите пароль"
                />
              </label>
              {error && (
                <p role="alert" className="error">
                  {error}
                </p>
              )}
              <button className="primary full" disabled={busy}>
                {busy ? "Входим…" : "Войти в SmartRuble"}
              </button>
            </form>
            <p className="login-note">
              Доступ выдаёт администратор вашей компании.
            </p>
          </div>
        </section>
      </div>
    );
  return (
    <div className="shell">
      <aside className="sidebar">
        <a className="brand" href="/">
          <span className="brand-icon">₽</span> SmartRuble
        </a>
        <div className="workspace">
          <div className="workspace-icon">
            <Building2 size={20} />
          </div>
          <div>
            Рабочее пространство<small>Контроль расходов</small>
          </div>
        </div>
        <span className="nav-title">УПРАВЛЕНИЕ</span>
        {[
          ["overview", "Обзор"],
          ["counterparties", "Контрагенты"],
          ["workorders", "Распоряжения"],
          ["payments", "Платежи"],
          ["approvals", "Согласование"],
          ["notifications", "Уведомления"],
        ]
          .filter(
            ([key]) =>
              !["approvals", "notifications"].includes(key) || canApprove,
          )
          .map(([key, title]) => (
            <button
              key={key}
              className={"nav-item " + (section === key ? "active" : "")}
              onClick={() => {
                setSection(key);
                setDetail(null);
                setModal(null);
              }}
            >
              <LayoutDashboard size={18} />
              {title}
            </button>
          ))}
        <div className="sidebar-help">
          <ShieldCheck size={22} />
          <strong>Проверка до перевода</strong>
          <p>
            Статус контрагента помогает принять обоснованное решение об оплате.
          </p>
        </div>
        <div className="profile">
          <div className="avatar">СР</div>
          <div>
            <strong>{user.phoneNumber}</strong>
            <small>{canApprove ? "Финансовый контроль" : "Сотрудник"}</small>
          </div>
          <button
            className="icon"
            aria-label="Выйти"
            onClick={async () => {
              try {
                await logout();
                setError("");
              } catch {
                setError(
                  "Локальный выход выполнен. Отозвать refresh-токен на сервере не удалось.",
                );
              } finally {
                setUser(null);
                setDetail(null);
                setRows([]);
                setModal(null);
              }
            }}
          >
            <LogOut size={18} />
          </button>
        </div>
      </aside>
      <main>
        <header className="topbar">
          <span>
            Компания <span className="slash">/</span> {{overview: "Обзор", counterparties: "Контрагенты", workorders: "Распоряжения", payments: "Платежи", approvals: "Согласования", notifications: "Уведомления"}[section]}
          </span>
          <span className="secure">
            <ShieldCheck size={16} /> Рабочий кабинет
          </span>
        </header>
        {section === "counterparties" ? (
          <div className="content">
            <div className="page-heading">
              <div>
                <span className="eyebrow">РЕЕСТР КОМПАНИИ</span>
                <h1>Контрагенты</h1>
                <p className="muted">
                  Проверяйте партнёров. Держите реквизиты в порядке.
                </p>
              </div>
              {canWrite && (
                <button className="primary" onClick={() => open("create")}>
                  <Plus size={18} />
                  Добавить контрагента
                </button>
              )}
            </div>
            <div className="overview">
              <div className="overview-main">
                <span className="overview-icon">
                  <Building2 />
                </span>
                <div>
                  <small>Найдено контрагентов</small>
                  <strong>{loading ? "—" : total}</strong>
                </div>
                <span className="overview-caption">С учётом фильтров</span>
              </div>
              <div className="overview-note">
                <ShieldCheck />
                <div>
                  <strong>Решения с историей</strong>
                  <p>
                    Изменения статуса сохраняются вместе с автором и причиной.
                  </p>
                </div>
              </div>
            </div>
            {error && (
              <div className="error" role="alert">
                {error}{" "}
                <button onClick={() => setVersion((v) => v + 1)}>
                  Повторить
                </button>
              </div>
            )}
            {notice && (
              <div className="notice" role="status">
                <Check size={16} />
                {notice}
                <button
                  className="icon"
                  onClick={() => setNotice("")}
                  aria-label="Закрыть сообщение"
                >
                  <X size={16} />
                </button>
              </div>
            )}
            <section className="registry">
              <div className="filters">
                <div className="tabs">
                  {[
                    ["", "Все"],
                    ["NEW", "Новые"],
                    ["VERIFIED", "Проверенные"],
                    ["SUSPICIOUS", "Внимание"],
                    ["BLOCKED", "Заблокированные"],
                  ].map(([value, label]) => (
                    <button
                      key={value}
                      className={status === value ? "selected" : ""}
                      onClick={() => {
                        setStatus(value);
                        setPage(0);
                      }}
                    >
                      {label}
                    </button>
                  ))}
                </div>
                <div className="search">
                  <Search size={18} />
                  <input
                    aria-label="Поиск контрагентов"
                    placeholder="Название или ИНН"
                    value={search}
                    onChange={(e) => {
                      setSearch(e.target.value);
                      setPage(0);
                    }}
                  />
                </div>
                <button
                  className="icon"
                  aria-label="Обновить список"
                  onClick={() => setVersion((v) => v + 1)}
                >
                  <RefreshCw size={18} />
                </button>
              </div>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Контрагент</th>
                      <th>ИНН / КПП</th>
                      <th>Статус проверки</th>
                      <th>Контакт</th>
                      <th>
                        <span className="sr-only">Открыть</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {!loading &&
                      rows.map((p) => (
                        <tr key={p.id}>
                          <td>
                            <button
                              className="party-link"
                              onClick={() => show(p.id)}
                            >
                              <span className="company-icon">
                                <Building2 size={20} />
                              </span>
                              <span>
                                <strong>{p.shortName || p.name}</strong>
                                <small>{types[p.type]}</small>
                              </span>
                            </button>
                          </td>
                          <td className="numeric">
                            {p.inn}
                            <small>{p.kpp || "—"}</small>
                          </td>
                          <td>
                            <Badge status={p.verificationStatus} />
                          </td>
                          <td>
                            {p.contactEmail || "—"}
                            <small>{p.contactPhone}</small>
                          </td>
                          <td>
                            <button
                              className="icon"
                              aria-label={"Открыть " + p.name}
                              onClick={() => show(p.id)}
                            >
                              <ArrowUpRight size={18} />
                            </button>
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
              {(loading || !rows.length) && (
                <div className="empty">
                  <LayoutDashboard size={30} />
                  <h3>
                    {loading
                      ? "Загружаем реестр…"
                      : error
                        ? "Не удалось загрузить реестр"
                        : "Контрагенты не найдены"}
                  </h3>
                  <p>
                    {!loading && !error
                      ? "Измените фильтры или добавьте первого партнёра."
                      : " "}
                  </p>
                </div>
              )}
              <footer className="pagination">
                <span>
                  {total
                    ? page * 12 +
                      1 +
                      "–" +
                      Math.min((page + 1) * 12, total) +
                      " из " +
                      total
                    : "0 записей"}
                </span>
                <div>
                  <button
                    className="icon"
                    disabled={!page || loading}
                    aria-label="Предыдущая страница"
                    onClick={() => setPage((p) => p - 1)}
                  >
                    <ChevronLeft size={18} />
                  </button>
                  <span>Страница {page + 1}</span>
                  <button
                    className="icon"
                    disabled={page + 1 >= pages || loading}
                    aria-label="Следующая страница"
                    onClick={() => setPage((p) => p + 1)}
                  >
                    <ChevronRight size={18} />
                  </button>
                </div>
              </footer>
            </section>
            <p className="bottom-note">
              <ShieldCheck size={14} /> Доступ к операциям определяется вашей
              ролью в компании.
            </p>
          </div>
        ) : (
          <Workbench key={section} section={section} user={user} />
        )}
      </main>
      {detail && !modal && (
        <Modal
          title={detail.counterparty.shortName || detail.counterparty.name}
          close={() => {
            selected.current++;
            setDetail(null);
          }}
        >
          <div className="detail-body">
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <Badge status={detail.counterparty.verificationStatus} />
            <p>{detail.counterparty.name}</p>
            <dl>
              {[
                ["ИНН", detail.counterparty.inn],
                ["КПП", detail.counterparty.kpp],
                ["ОГРН", detail.counterparty.ogrn],
                ["Адрес", detail.counterparty.legalAddress],
                ["Email", detail.counterparty.contactEmail],
                ["Телефон", detail.counterparty.contactPhone],
              ].map(([k, v]) => (
                <div key={k}>
                  <dt>{k}</dt>
                  <dd>{v || "—"}</dd>
                </div>
              ))}
            </dl>
            <div className="actions">
              {canWrite && (
                <button onClick={() => open("edit")}>Редактировать</button>
              )}
              {canApprove && (
                <button onClick={() => open("status")}>Изменить статус</button>
              )}
            </div>
            <div className="section-head">
              <h3>
                <Landmark size={18} />
                Банковские счета
              </h3>
              {canWrite && (
                <button onClick={() => open("bank")}>
                  <Plus size={16} />
                  Добавить
                </button>
              )}
            </div>
            {!detail.bankAccounts.length && (
              <p className="muted">Банковские счета ещё не добавлены.</p>
            )}
            {detail.bankAccounts.map((b) => (
              <div className="bank" key={b.id}>
                <strong>{b.bankName}</strong>
                <p className="numeric">{b.accountNumber}</p>
                <small>
                  БИК {b.bik} ·{" "}
                  {b.active
                    ? b.primary
                      ? "Основной"
                      : "Действующий"
                    : "Отключён"}
                </small>
                {canWrite && b.active && (
                  <div className="actions">
                    {!b.primary && (
                      <button
                        disabled={busy}
                        onClick={() => bankAction(b.id, "MAKE_PRIMARY")}
                      >
                        Сделать основным
                      </button>
                    )}
                    <button
                      disabled={busy}
                      onClick={() => bankAction(b.id, "DEACTIVATE")}
                    >
                      Отключить
                    </button>
                  </div>
                )}
              </div>
            ))}
            {canHistory && (
              <>
                <h3>История проверки</h3>
                {history.map((h) => (
                  <div className="history" key={h.id}>
                    <Badge status={h.newStatus} />
                    <small>{date(h.changedAt)}</small>
                    <p>{h.reason || "Изменение статуса"}</p>
                    <small>Автор · {h.changedBy}</small>
                  </div>
                ))}
              </>
            )}
          </div>
        </Modal>
      )}
      {modal && (
        <Modal
          title={
            {
              create: "Новый контрагент",
              edit: "Реквизиты контрагента",
              bank: "Новый банковский счёт",
              status: "Решение по контрагенту",
            }[modal]
          }
          close={() => {
            if (!busy) setModal(null);
          }}
        >
          <form className="detail-body" onSubmit={save}>
            {(modal === "create" || modal === "edit") && (
              <div className="form-grid">
                {modal === "create" && (
                  <>
                    <label>
                      Тип
                      <select name="type">
                        {Object.entries(types).map(([k, v]) => (
                          <option key={k} value={k}>
                            {v}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      ИНН
                      <input
                        name="inn"
                        required
                        pattern="[0-9]{10}|[0-9]{12}"
                        inputMode="numeric"
                        maxLength={12}
                      />
                    </label>
                  </>
                )}
                {[
                  ["name", "Полное наименование"],
                  ["shortName", "Краткое наименование"],
                  ["kpp", "КПП"],
                  ["ogrn", "ОГРН / ОГРНИП"],
                  ["legalAddress", "Юридический адрес"],
                  ["contactEmail", "Email"],
                  ["contactPhone", "Телефон"],
                ].map(([k, label]) => (
                  <label key={k}>
                    {label}
                    <input
                      name={k}
                      required={k === "name"}
                      type={k === "contactEmail" ? "email" : "text"}
                      maxLength={
                        k === "legalAddress"
                          ? 500
                          : k === "contactEmail"
                            ? 100
                            : k === "contactPhone"
                              ? 20
                              : 255
                      }
                      defaultValue={
                        modal === "edit"
                          ? String(detail?.counterparty[k as keyof Party] || "")
                          : ""
                      }
                    />
                  </label>
                ))}
              </div>
            )}
            {modal === "bank" && (
              <div className="form-grid">
                {[
                  ["bankName", "Название банка", ""],
                  ["accountNumber", "Расчётный счёт", "[0-9]{20}"],
                  ["bik", "БИК", "[0-9]{9}"],
                  [
                    "correspondentAccount",
                    "Корреспондентский счёт",
                    "[0-9]{20}",
                  ],
                ].map(([name, label, pattern]) => (
                  <label key={name}>
                    {label}
                    <input
                      name={name}
                      pattern={pattern || undefined}
                      required={name !== "correspondentAccount"}
                      maxLength={
                        name === "bankName" ? 255 : name === "bik" ? 9 : 20
                      }
                    />
                  </label>
                ))}
              </div>
            )}
            {modal === "status" && (
              <>
                <label>
                  Новый статус
                  <select
                    name="status"
                    value={nextStatus}
                    onChange={(e) => setNextStatus(e.target.value as Status)}
                  >
                    {Object.entries(labels).map(([k, v]) => (
                      <option key={k} value={k}>
                        {v}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Причина решения
                  <textarea
                    name="reason"
                    maxLength={1000}
                    required={
                      nextStatus === "BLOCKED" || nextStatus === "SUSPICIOUS"
                    }
                    placeholder="Укажите основания решения"
                  />
                </label>
              </>
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
                onClick={() => setModal(null)}
              >
                Отмена
              </button>
              <button className="primary" disabled={busy}>
                {busy ? "Сохраняем…" : "Сохранить"}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
