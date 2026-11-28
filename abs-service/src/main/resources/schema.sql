create table if not exists bank_balance(id integer primary key,balance numeric(19,2) not null check(balance>=0));
insert into bank_balance values(1,10000000.00) on conflict do nothing;
create table if not exists bank_transfers(payment_id uuid primary key,amount numeric(19,2) not null,account varchar(20) not null,bik varchar(9) not null,status varchar(20) not null,reference uuid not null,created_at timestamptz not null default now());
