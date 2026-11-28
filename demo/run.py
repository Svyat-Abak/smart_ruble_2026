#!/usr/bin/env python3
"""Local demo fixtures and end-to-end checks. Uses only the opt-in demo accounts."""
import json, os, sys, time, uuid, urllib.request, urllib.error
from decimal import Decimal
BASE = os.getenv("GATEWAY_URL", "http://127.0.0.1:8080") + "/api/v1"
PASSWORD = "Demo12345!"
class Client:
    def __init__(self, phone):
        self.token = None
        auth = self.call("POST", "/auth/login", {"phoneNumber": phone, "password": PASSWORD})
        self.token, self.user = auth["accessToken"], auth["user"]
    def call(self, method, path, body=None, headers=None, expected=None):
        hdr = {"Content-Type": "application/json", **(headers or {})}
        if self.token: hdr["Authorization"] = "Bearer " + self.token
        req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=hdr, method=method)
        try:
            with urllib.request.urlopen(req, timeout=15) as res:
                status, raw = res.status, res.read()
        except urllib.error.HTTPError as err:
            status, raw = err.code, err.read()
        if expected is not None:
            assert status == expected, f"{method} {path}: expected {expected}, got {status}: {raw.decode()}"
        elif status >= 400:
            raise RuntimeError(f"{method} {path}: {status}: {raw.decode()}")
        return json.loads(raw) if raw else None

def inn(n):
    value = "99000000" + str(n)
    return value + str(sum(int(a)*b for a,b in zip(value,[2,4,10,3,5,9,4,6,8])) % 11 % 10)

def seed():
    manager, finance, contractor, employee = [Client("+7000000000"+str(i)) for i in range(1,5)]
    parties = []
    for n, (name, status) in enumerate([
        ("[DEMO] СтройКонтур — подрядчик", "VERIFIED"),
        ("[DEMO] Материалы — поставщик", "VERIFIED"),
        ("[DEMO] Поставка с проверкой", "SUSPICIOUS"),
        ("[DEMO] Заблокированный поставщик", "BLOCKED")], 1):
        existing = manager.call("GET", "/billing/counterparties?size=200&search=%5BDEMO%5D")["content"]
        party = next((p for p in existing if p["inn"] == inn(n)), None)
        if party is None:
            party = manager.call("POST", "/billing/counterparties", {"type":"LEGAL_ENTITY", "name":name, "inn":inn(n), "kpp":"990001001"})
        path = "/billing/counterparties/"+party["id"]
        if not manager.call("GET", path)["bankAccounts"]:
            manager.call("POST", path+"/bank-accounts", {"accountNumber":"4070281000000000000"+str(n), "bik":"044525225", "bankName":"Демонстрационный банк (mock)"})
        if party["verificationStatus"] != status:
            finance.call("POST", path+"/status", {"status":status,"reason":"Вымышленный сценарий стенда. Не результат проверки ФНС."})
        parties.append(party)
    manager.call("POST", "/billing/counterparties/"+parties[0]["id"]+"/members", {"userId":contractor.user["id"]})
    title = "[DEMO] Ремонт инженерных систем"
    orders = manager.call("GET", "/billing/workorders")
    order = next((o for o in orders if o["title"] == title), None)
    if order is None:
        order = manager.call("POST", "/billing/workorders", {"title":title,"workObject":"Учебный корпус, секция А","contractorId":parties[0]["id"],"lines":[{"category":"MATERIALS","limit":250000},{"category":"LABOR","limit":150000},{"category":"EQUIPMENT","limit":100000}]})
    if order["status"] == "DRAFT": finance.call("POST", "/billing/workorders/"+order["id"]+"/status", {"status":"ACTIVE"})
    manager.call("POST", "/billing/workorders/"+order["id"]+"/assignees", {"userId":employee.user["id"]})
    print("Демо готово. Компании вымышленные, реквизиты предназначены только для mock.")
    for label, client in zip(["manager","finance","contractor","employee"],[manager,finance,contractor,employee]):
        print(label+": "+client.user["id"])
    print("Распоряжение: "+order["id"])
    return manager, finance, contractor, employee, parties

def bank(path, body=None):
    url = os.getenv("BANK_URL", "http://abs-service:8083")+"/internal"+path
    req = urllib.request.Request(url, data=None if body is None else json.dumps(body).encode(), headers={"Content-Type":"application/json","X-Service-Key":os.getenv("BANK_KEY","local-demo-bank-key")}, method="GET" if body is None else "POST")
    with urllib.request.urlopen(req,timeout=10) as res: return json.load(res)

def smoke():
    manager, finance, contractor, employee, parties = seed()
    order = manager.call("POST", "/billing/workorders", {"title":"[CHECK] "+str(uuid.uuid4())[:8],"workObject":"Автоматическая проверка","contractorId":parties[0]["id"],"lines":[{"category":"MATERIALS","limit":100000}]})
    oid = order["id"]; line = order["lines"][0]["id"]
    finance.call("POST", "/billing/workorders/"+oid+"/status", {"status":"ACTIVE"})
    employee.call("GET", "/billing/workorders/"+oid, expected=404)
    checks = []
    def create(amount, recipient=1, key=None, expected=None):
        return contractor.call("POST", "/billing/payments", {"workOrderId":oid,"budgetLineId":line,"recipientId":parties[recipient]["id"],"amount":amount,"purpose":"Автоматическая проверка MVP"}, {"Idempotency-Key":key or str(uuid.uuid4())}, expected)
    def wait(pid, target):
        until = time.monotonic()+40
        while time.monotonic()<until:
            payment = contractor.call("GET", "/billing/payments/"+pid)
            if payment["status"] == target: return payment
            if payment["status"] in {"FAILED","REJECTED","BLOCKED"}: raise AssertionError(payment)
            time.sleep(.5)
        raise AssertionError("Timeout: "+str(payment))
    key = str(uuid.uuid4()); p = create(1000,key=key)
    assert create(1000,key=key)["id"] == p["id"]
    create(1001,key=key,expected=409); wait(p["id"],"SUCCEEDED")
    checks.append("Повтор запроса не создает второй платёж; конфликт payload — 409")
    assert create(1000,3)["status"] == "BLOCKED"
    assert create(100000)["status"] == "BLOCKED"
    checks.append("Заблокированный получатель и превышение бюджета запрещены")
    approval = create(60000); assert approval["status"] == "PENDING_APPROVAL"
    contractor.call("POST", "/billing/payments/"+approval["id"]+"/decision", {"approve":True,"comment":"Нельзя"}, expected=403)
    finance.call("POST", "/billing/payments/"+approval["id"]+"/decision", {"approve":True,"comment":"Проверка сметы"})
    wait(approval["id"],"SUCCEEDED"); checks.append("Согласование финансовым директором; подрядчику запрещено — 403")
    rejected=create(1000,2)
    finance.call("POST", "/billing/payments/"+rejected["id"]+"/decision", {"approve":False,"comment":"Отказ"})
    checks.append("Отказ по подозрительному получателю освобождает резерв")
    bank("/demo/mode", {"mode":"REJECT_ONCE"}); decline=create(2000); wait(decline["id"],"FAILED")
    bank("/demo/mode", {"mode":"TIMEOUT_AFTER_COMMIT"}); lost=create(3000); wait(lost["id"],"SUCCEEDED")
    transfers=[x for x in bank("/ledger")["transfers"] if x["payment_id"]==lost["id"]]
    assert len(transfers)==1
    checks.append("Отказ банка и потеря ответа после commit: повтор без второго списания")
    line_state=manager.call("GET", "/billing/workorders/"+oid)["lines"][0]
    assert Decimal(str(line_state["spent_amount"]))==64000 and Decimal(str(line_state["reserved_amount"]))==0
    assert Decimal(str(line_state["available"]))==36000
    checks.append("Баланс статьи: потрачено 64000, резерв 0, доступно 36000")
    until=time.monotonic()+40
    while time.monotonic()<until:
        notices=finance.call("GET", "/notifications")
        if any(n["payment_id"]==lost["id"] and n["status"]=="SUCCEEDED" for n in notices): break
        time.sleep(.5)
    else: raise AssertionError("Kafka notification did not arrive")
    assert len({n["id"] for n in notices})==len(notices)
    employee.call("GET", "/notifications",expected=403)
    checks.append("Событие дошло через Kafka в журнал; сотруднику журнал запрещен")
    manager.call("POST", "/billing/workorders/"+oid+"/status", {"status":"CLOSED"})
    for check in checks: print("PASS: "+check)
    print("SMOKE PASSED — "+str(len(checks))+" групп проверок")

if __name__ == "__main__":
    try:
        mode = sys.argv[1] if len(sys.argv)>1 else "seed"
        if mode == "seed": seed()
        elif mode == "smoke": smoke()
        elif mode == "bank-mode": print(bank("/demo/mode", {"mode":sys.argv[2]}))
        elif mode == "ledger": print(json.dumps(bank("/ledger"),ensure_ascii=False,indent=2))
        else: raise ValueError("Use seed | smoke | bank-mode NORMAL/REJECT_ONCE/TIMEOUT_AFTER_COMMIT | ledger")
    except Exception as error:
        print("FAIL:",error,file=sys.stderr);sys.exit(1)
