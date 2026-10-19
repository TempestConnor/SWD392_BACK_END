-- F05/F06: matchmaking thresholds for the launch CLASSICAL/STANDARD queue (BR-CONFIG-001, BR-RATE-016/017/019).
-- Product decisions recorded 2026-10-19: clients send a queue heartbeat every 10 seconds and a request silent
-- for 10 + 20 seconds times out; pairing starts within 100 Elo and widens by 50 every 30 seconds up to 400.
-- Idempotent: inserts the row only when it is missing, so values changed later by an operator are kept.
IF NOT EXISTS (SELECT 1 FROM dbo.MatchmakingConfig WHERE mode_code = 'CLASSICAL' AND variant_code = 'STANDARD')
BEGIN
    INSERT INTO dbo.MatchmakingConfig
        (mode_code, variant_code, initial_gap, gap_step, gap_step_seconds, max_gap,
         heartbeat_seconds, queue_grace_seconds, ready_timeout_seconds)
    VALUES ('CLASSICAL', 'STANDARD', 100, 50, 30, 400, 10, 20, 180);
END;
