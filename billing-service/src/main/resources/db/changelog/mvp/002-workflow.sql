--liquibase formatted sql
--changeset smartruble:mvp-workflow-1
create table work_orders (
 id uuid primary key, title varchar(255) not null, work_object varchar(500) not null,
 contractor_id uuid not null, manager_id uuid not null, total_budget numeric(19,2) not null check(total_budget>0),
 status varchar(20) not null check(status in ('DRAFT','ACTIVE','CLOSED')), created_at timestamptz not null default now()
);
create table budget_lines (
 id uuid primary key, work_order_id uuid not null references work_orders(id), category varchar(30) not null,
 limit_amount numeric(19,2) not null check(limit_amount>0), reserved_amount numeric(19,2) not null default 0 check(reserved_amount>=0),
 spent_amount numeric(19,2) not null default 0 check(spent_amount>=0),
 unique(work_order_id,category), check(reserved_amount+spent_amount<=limit_amount)
);
create table work_order_assignees(work_order_id uuid references work_orders(id),user_id uuid,primary key(work_order_id,user_id));
create table work_order_status_history(id uuid primary key,work_order_id uuid references work_orders(id),status varchar(20) not null,changed_by uuid not null,reason text,changed_at timestamptz not null default now());
create table payment_requests (
 id uuid primary key, idempotency_key uuid not null, created_by uuid not null, work_order_id uuid not null,
 budget_line_id uuid not null, recipient_id uuid not null, recipient_name varchar(255) not null, recipient_inn varchar(12) not null,
 recipient_account varchar(20),recipient_bik varchar(9), amount numeric(19,2) not null check(amount>0),purpose varchar(500) not null,
 status varchar(30) not null check(status in ('BLOCKED','PENDING_APPROVAL','READY','PROCESSING','SUCCEEDED','FAILED','REJECTED')),
 decision varchar(30) not null,reason text not null,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 attempts integer not null default 0,last_error text,bank_reference uuid,unique(created_by,idempotency_key)
);
create index idx_payments_pending on payment_requests(status,updated_at);
create table approval_tasks(payment_id uuid primary key references payment_requests(id),status varchar(20) not null,decided_by uuid,decided_at timestamptz,comment varchar(1000));
create table payment_status_history(id uuid primary key,payment_id uuid references payment_requests(id),status varchar(30) not null,reason text,changed_by uuid,changed_at timestamptz not null default now());
create table payment_outbox(sequence bigserial unique,id uuid primary key,payment_id uuid not null,event_type varchar(50) not null,payload text not null,created_at timestamptz not null default now(),published_at timestamptz);
create index idx_outbox_pending on payment_outbox(created_at) where published_at is null;
