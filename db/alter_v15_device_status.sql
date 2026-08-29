-- v15: 设备状态仅良好/故障
UPDATE device SET status = 'GOOD' WHERE status IN ('IDLE', 'IN_USE', 'GOOD') OR status IS NULL OR status = '';
UPDATE device SET status = 'FAULT' WHERE status IN ('MAINTENANCE', 'FAULT');
