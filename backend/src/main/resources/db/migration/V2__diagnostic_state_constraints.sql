alter table diagnostic_task
    add constraint ck_diagnostic_task_state
        check (state in ('CREATED', 'INVESTIGATING', 'REPORT_READY', 'COMPLETED', 'INCOMPLETE'));

create index idx_audit_event_correlation_time
    on audit_event(correlation_id, occurred_at);
