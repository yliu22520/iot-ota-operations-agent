alter table upgrade_task
    add column retry_count integer not null default 0,
    add column max_retries integer not null default 3;

alter table diagnostic_task drop constraint ck_diagnostic_task_state;
alter table diagnostic_task
    add constraint ck_diagnostic_task_state
        check (state in ('CREATED', 'INVESTIGATING', 'REPORT_READY', 'WAITING_APPROVAL',
                         'EXECUTING', 'VERIFYING', 'COMPLETED', 'INCOMPLETE'));

alter table retry_plan
    add column approved_by varchar(128),
    add column approved_at timestamptz;

create table simulator_retry_attempt (
    id uuid primary key,
    diagnostic_task_id uuid not null,
    source_upgrade_task_id uuid not null references upgrade_task(id),
    target_version varchar(64) not null,
    idempotency_key varchar(255) not null unique,
    status varchar(32) not null,
    created_at timestamptz not null
);

create index idx_simulator_retry_attempt_diagnostic
    on simulator_retry_attempt(diagnostic_task_id, created_at);
