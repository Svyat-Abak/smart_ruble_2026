import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import ts from "typescript";
const source = fs.readFileSync(
  new URL("../lib/api.ts", import.meta.url),
  "utf8",
);
async function client() {
  const js = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
  return import(
    "data:text/javascript;base64," +
      Buffer.from(js + "\n// " + Math.random()).toString("base64")
  );
}
const auth = {
  accessToken: "access-old",
  refreshToken: "refresh-old",
  user: { id: "test", phoneNumber: "test", roles: [] },
};
test("late 401 reuses rotated session without replaying old refresh token", async () => {
  const api = await client();
  api.setSession(auth);
  let release;
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  let rotations = 0;
  globalThis.fetch = async (url, init) => {
    if (url.endsWith("/auth/refresh")) {
      rotations++;
      return Response.json({
        ...auth,
        accessToken: "access-new",
        refreshToken: "refresh-new",
      });
    }
    if (init.headers.Authorization === "Bearer access-old") {
      if (url.endsWith("/slow")) await gate;
      return new Response("", { status: 401 });
    }
    return Response.json({ ok: true });
  };
  const slow = api.request("/slow");
  await api.request("/fast");
  release();
  assert.deepEqual(await slow, { ok: true });
  assert.equal(rotations, 1);
});
test("failed refresh clears session and emits expiration", async () => {
  const api = await client();
  api.setSession(auth);
  const events = [];
  globalThis.window = {
    dispatchEvent(event) {
      events.push(event.type);
    },
  };
  globalThis.fetch = async () =>
    Response.json({ message: "Expired" }, { status: 401 });
  await assert.rejects(api.request("/users/me"), /Сессия истекла/);
  assert.deepEqual(events, ["session-expired"]);
});
test("server field validation reaches the form", async () => {
  const api = await client();
  globalThis.fetch = async () =>
    Response.json(
      { fieldErrors: { inn: "Некорректный ИНН" } },
      { status: 400 },
    );
  await assert.rejects(
    api.request("/billing/counterparties"),
    /Некорректный ИНН/,
  );
});
test("logout accepts an empty 204 response", async () => {
  const api = await client();
  api.setSession(auth);
  globalThis.fetch = async () => new Response(null, { status: 204 });
  await api.logout();
});

test("empty 201 does not fail after a successful command", async () => {
  const api = await client();
  globalThis.fetch = async () => new Response(null, { status: 201 });
  assert.equal(await api.request("/billing/counterparties/x/members", {method:"POST"}), undefined);
});
