-- Fix 1: Extend data_access_logs check constraint to include governance access types.
ALTER TABLE data_access_logs DROP CONSTRAINT chk_data_access_type;
ALTER TABLE data_access_logs ADD CONSTRAINT chk_data_access_type
    CHECK (access_type IN (
        'READ', 'EXPORT', 'MASKED_READ', 'UNMASKED_READ',
        'GOVERNANCE_EXPORT', 'GOVERNANCE_IMPORT', 'GOVERNANCE_HISTORY_READ',
        'GOVERNANCE_PURGE', 'GOVERNANCE_RESTORE', 'GOVERNANCE_RECYCLE_READ',
        'GOVERNANCE_SCAN'
    ));
