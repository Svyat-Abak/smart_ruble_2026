create table if not exists notifications(id uuid primary key,payment_id uuid not null,status varchar(30) not null,message text not null,received_at timestamptz not null default now());
